
import bot.Bot
import cats.effect.std.Semaphore
import cats.effect.{ExitCode, IO, IOApp}
import clients.{MongoPersistenceClient, PersistenceClient, SpotifyClient, YoutubeClient}
import com.typesafe.scalalogging.Logger
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession
import trackscollector.TracksCollector
import tracksdownloader.TracksDownloader

import java.util.concurrent.Executors
import scala.concurrent.duration.DurationInt
import scala.concurrent.ExecutionContext

object Main extends IOApp {

  private val log = Logger(this.getClass.getSimpleName)

  private val ec = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

  override def run(args: List[String]): IO[ExitCode] = {
    def collectorIO(s: Semaphore[IO], tracksCollector: TracksCollector): IO[Unit] = {
      for {
        isNonRunning <- s.tryAcquire
        _            <- if (isNonRunning) {
          (tracksCollector.collectTracks() *> s.release).start
        } else IO(log.warn("previous collect job not finished yet"))
        _            <- IO.sleep(1.hour)
      } yield ()
    }

    def botInit(spotifyClient: SpotifyClient, youtubeClient: YoutubeClient, persistenceClient: PersistenceClient): Bot = {
      val bot = new Bot(sys.env("TRACKS_COLLECTOR_BOT_TOKEN"), spotifyClient, youtubeClient, persistenceClient)
      val botsApi = new TelegramBotsApi(classOf[DefaultBotSession])
      botsApi.registerBot(bot)
      log.info("bot started")
      bot
    }
    val res = for {
      semaphore         <- Semaphore[IO](1)
      spotifyClient     = new SpotifyClient(sys.env("SPOTIFY_CLIENT_ID"), sys.env("SPOTIFY_CLIENT_SECRET"))
      youtubeClient     = new YoutubeClient(sys.env("YOUTUBE_API_KEY"))
      persistenceClient = new MongoPersistenceClient(sys.env("MONGO_URI"))
      downloader        = new TracksDownloader
      bot               = botInit(spotifyClient, youtubeClient, persistenceClient)
      tracksCollector   = new TracksCollector(
        spotifyClient,
        youtubeClient,
        persistenceClient,
        downloader,
        bot,
        sys.env("TRACKS_DIR"),
      )
      _                 <- IO.sleep(5.minutes) *> collectorIO(semaphore, tracksCollector).foreverM
    } yield ExitCode.Success
    res.evalOn(ec)
  }
}
