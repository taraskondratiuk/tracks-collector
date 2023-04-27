package trackscollector

import bot.Bot
import cats.effect.IO
import cats.effect.std.Semaphore
import cats.syntax.traverse.toTraverseOps
import clients.{PersistenceClient, SpotifyClient, YoutubeClient}
import com.typesafe.scalalogging.Logger
import models.{SpotifySource, TrackFilesGroup, TrackSource, YoutubeSource}
import tracksdownloader.TracksDownloader

import java.io.File
import scala.concurrent.duration.DurationInt

class TracksCollector(spotifyClient: SpotifyClient,
                      youtubeClient: YoutubeClient,
                      persistenceClient: PersistenceClient,
                      downloader: TracksDownloader,
                      bot: Bot,
                      tracksDir: String,
                     ) {

  private val log = Logger(this.getClass.getSimpleName)

  private case class Chat(chatId: String)
  private case class TracksFromPlaylist(tracks: Seq[Track], playlistRecordId: String)
  private case class Track(url: String, source: TrackSource, maybeTrackName: Option[String] = None)

  def collectTracks(): IO[Unit] = {
    for {
      _                <- IO(log.info("start collecting tracks"))
      tsStarted        = System.currentTimeMillis() / 1000
      tracksGroupedByChatId = persistenceClient.getAllPlaylistRecords().map { p =>
        val tracks = p.source match {
          case SpotifySource =>
            spotifyClient
              .getSpotifyTrackUrlsFromPlaylist(p.playlistId, p.tsLastSave)
              .map(url => Track(url, SpotifySource))
          case YoutubeSource =>
            youtubeClient
              .getYoutubeTracksInfoFromPlaylist(p.playlistId, p.tsLastSave)
              .map(ytTrackInfo => Track(ytTrackInfo.url, YoutubeSource, Some(ytTrackInfo.name)))
        }
        Chat(p.chatId) -> TracksFromPlaylist(tracks, p._id)
      }.groupMapReduce { case (k, _) => k } { case (_, v) => Seq(v) } { case (values1, values2) => values1 ++ values2 }
      _                <- tracksGroupedByChatId.map { case (Chat(chatId), playlists) =>
        sendTracksForSingleChat(chatId, playlists, tsStarted)
          .handleError(e => log.info(s"failed to collect tracks for chatId $chatId: ${e.getMessage}"))
      }.toList.sequence
      _                = log.info("finish collecting tracks")
    } yield ()
  }

  private def sendTracksForSingleChat(chatId: String,
                                      playlists: Seq[TracksFromPlaylist],
                                      saveTime: Long,
                                      chatSemaphore: IO[Semaphore[IO]] = Semaphore[IO](5),
                                     ): IO[Unit] = {
    for {
      s        <- chatSemaphore
      chatPath = s"$tracksDir/$chatId"
      _        <- playlists
        .traverse(p => sendTracksForSingleChatFromSinglePlaylist(chatId, p.tracks, saveTime, p.playlistRecordId, chatPath, s))
    } yield ()
  }

  private def sendTracksForSingleChatFromSinglePlaylist(chatId: String,
                                                        tracks: Seq[Track],
                                                        saveTime: Long,
                                                        playlistRecordId: String,
                                                        chatPath: String,
                                                        chatSemaphore: Semaphore[IO],
                                                       ): IO[Unit] = IO.defer {
    val chatTracksDir = new File(chatPath)
    chatTracksDir.mkdirs
    chatTracksDir.listFiles.filter(_.isFile).map(_.delete())
    tracks.foreach(v => downloader.downloadTrack(v.url, chatPath, v.source, v.maybeTrackName))
    IO.whenA(tracks.nonEmpty) {
      log.info(s"sending tracks from $chatPath")

      val files = chatTracksDir
        .listFiles
        .filter(_.isFile)
        .toList

      val tracksGroups = TrackFilesGroup.generateGroupsFromFiles(files)

      val sendTracksIO = tracksGroups.traverse { tracksGroup =>
        val sendTracksIO = IO {
          log.info(s"sending $tracksGroup")
          bot.sendTracks(tracksGroup, chatId)
        }
        for {
          _ <- chatSemaphore.acquire
          _ <- IO.sleep(10.seconds)
          _ <- sendTracksIO.handleErrorWith { e =>
            val timeoutRegex = ".*retry after (\\d+).*".r
            e.getMessage match {
              case timeoutRegex(t) =>
                val timeout = t.toInt + 5
                log.warn(s"tg api timeout: ${e.getMessage}, waiting $timeout seconds before retry")
                IO.sleep(timeout.seconds) *> sendTracksIO
              case _               =>
                IO.raiseError(e)
            }
          }
          //tg bot limit is 20 msgs per minute for single chat
          _ <- (IO.sleep(120.seconds) *> chatSemaphore.release).start
        } yield ()
      }
      sendTracksIO.map(_ => persistenceClient.updateSaveTimeForPlaylist(playlistRecordId, saveTime))
    }
  }
}
