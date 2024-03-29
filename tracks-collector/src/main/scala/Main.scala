import bot.Bot
import cats.effect.std.Semaphore
import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits.catsSyntaxParallelSequence1
import clients.{MongoPersistenceClient, PersistenceClient, SpotifyClient, YoutubeClient}
import com.typesafe.scalalogging.Logger
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession
import trackscollector.TracksCollector
import tracksdownloader.TracksDownloader

import scala.concurrent.duration.DurationInt

object Main extends IOApp {

  private val TRACKS_COLLECTOR_BOT_TOKEN = sys.env("TRACKS_COLLECTOR_BOT_TOKEN")
  private val SPOTIFY_CLIENT_ID          = sys.env("SPOTIFY_CLIENT_ID")
  private val SPOTIFY_CLIENT_SECRET      = sys.env("SPOTIFY_CLIENT_SECRET")
  private val YOUTUBE_API_KEY            = sys.env("YOUTUBE_API_KEY")
  private val MONGO_URI                  = sys.env("MONGO_URI")
  private val TRACKS_DIR                 = sys.env("TRACKS_DIR")

  private val log = Logger(this.getClass.getSimpleName)

  override def run(args: List[String]): IO[ExitCode] = {
    def trackedPlaylistsCollectorIO(s: Semaphore[IO], tracksCollector: TracksCollector): IO[Unit] = {
      for {
        isNonRunning <- s.tryAcquire
        _            <- if (isNonRunning) {
          val collectIO = tracksCollector
            .collectTracksFromTrackedPlaylists()
            .handleError(e => log.warn(s"tracked tracks collector failed: ${e.getMessage}"))
          (collectIO *> s.release).start
        } else IO(log.warn("previous tracked collect job not finished yet"))
        _            <- IO.sleep(30.minutes)
      } yield ()
    }

    def untrackedPlaylistsCollectorIO(s: Semaphore[IO], tracksCollector: TracksCollector): IO[Unit] = {
      for {
        isNonRunning <- s.tryAcquire
        _            <- if (isNonRunning) {
          val downloadIO = tracksCollector
            .collectUntrackedTracksOrPlaylists()
            .handleError(e => log.warn(s"untracked tracks collector failed: ${e.getMessage}"))
          (downloadIO *> s.release).start
        } else IO(log.warn("previous untracked collect job not finished yet"))
        _            <- IO.sleep(1.minutes)
      } yield ()
    }

    def botInit(spotifyClient: SpotifyClient, youtubeClient: YoutubeClient, persistenceClient: PersistenceClient): Bot = {
      val bot = new Bot(TRACKS_COLLECTOR_BOT_TOKEN, spotifyClient, youtubeClient, persistenceClient)
      val botsApi = new TelegramBotsApi(classOf[DefaultBotSession])
      botsApi.registerBot(bot)
      log.info("bot started")
      bot
    }

    for {
      trackedPlaylistsSemaphore   <- Semaphore[IO](1)
      untrackedPlaylistsSemaphore <- Semaphore[IO](1)
      spotifyClient               = new SpotifyClient(SPOTIFY_CLIENT_ID, SPOTIFY_CLIENT_SECRET)
      youtubeClient               = new YoutubeClient(YOUTUBE_API_KEY)
      persistenceClient           = new MongoPersistenceClient(MONGO_URI)
      downloader                  = new TracksDownloader
      bot                         = botInit(spotifyClient, youtubeClient, persistenceClient)
      tracksCollector             = new TracksCollector(
        spotifyClient,
        youtubeClient,
        persistenceClient,
        downloader,
        bot,
        TRACKS_DIR,
      )
      _                           <- IO.sleep(2.minutes)
      _                           <- Seq(
        trackedPlaylistsCollectorIO(trackedPlaylistsSemaphore, tracksCollector).foreverM,
        untrackedPlaylistsCollectorIO(untrackedPlaylistsSemaphore, tracksCollector).foreverM,
      ).parSequence
    } yield ExitCode.Success
  }
}
