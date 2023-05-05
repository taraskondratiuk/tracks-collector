package trackscollector

import bot.Bot
import cats.effect.IO
import cats.effect.std.Semaphore
import cats.syntax.traverse.toTraverseOps
import clients.{PersistenceClient, SpotifyClient, YoutubeClient}
import com.typesafe.scalalogging.Logger
import models.{SpotifySource, TrackFilesGroup, TrackSource, UntrackedMedia, UntrackedPlaylistRecord, UntrackedTrackRecord, YoutubeSource}
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
  private case class MediaGroup(tracks: Seq[Track],
                                groupName: String,
                                groupUrl: String,
                                recordId: String,
                                mediaGroupType: MediaGroupType,
                               )
  private sealed trait MediaGroupType
  private case class Tracked(tsUpdate: Long) extends MediaGroupType
  private case class UntrackedTrack(tsInserted: Long) extends MediaGroupType
  private case class UntrackedPlaylist(tsInserted: Long) extends MediaGroupType
  private case class Track(url: String, source: TrackSource, maybeTrackName: Option[String] = None)

  def collectTracksFromTrackedPlaylists(): IO[Unit] = {
    for {
      _                     <- IO(log.info("start collecting tracked playlists"))
      tsStarted             = System.currentTimeMillis() / 1000
      tracksGroupedByChatId = persistenceClient.getAllTrackedPlaylistRecords().unsafeRun().map { p =>
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
        Chat(p.chatId) -> MediaGroup(tracks, p.name, p.playlistUrl, p._id, Tracked(tsStarted))
      }.groupMapReduce { case (k, _) => k } { case (_, v) => Seq(v) } { case (values1, values2) => values1 ++ values2 }
      _                     <- tracksGroupedByChatId.map { case (Chat(chatId), playlists) =>
        sendTracksForSingleChat(chatId, playlists, "tracked")
          .handleError(e => log.info(s"failed to collect tracked tracks for chatId $chatId: ${e.getMessage}"))
      }.toList.sequence
      _                     = log.info("finish collecting tracked playlists")
    } yield ()
  }

  def collectUntrackedTracksOrPlaylists(): IO[Unit] = {
    for {
      _ <- IO(log.info("start collecting untracked tracks/playlists"))
      tracksOrPlaylistsGroupedByChatId =
        (persistenceClient.getAllUntrackedTracksRecords().unsafeRun()
          ++ persistenceClient.getAllUntrackedPlaylistRecords().unsafeRun()).map { m: UntrackedMedia =>
          val (tracks, mediaGroupType) = (m, m.source) match {
            case (p: UntrackedPlaylistRecord, YoutubeSource) =>
              val tracks = youtubeClient
                .getYoutubeTracksInfoFromPlaylist(p.playlistId, 0L)
                .map(ytTrackInfo => Track(ytTrackInfo.url, YoutubeSource, Some(ytTrackInfo.name)))
              tracks -> UntrackedPlaylist(p.tsInserted)
            case (p: UntrackedPlaylistRecord, SpotifySource) =>
              val tracks = spotifyClient
                .getSpotifyTrackUrlsFromPlaylist(p.playlistId, 0L)
                .map(url => Track(url, SpotifySource))
              tracks -> UntrackedPlaylist(p.tsInserted)
            case (t: UntrackedTrackRecord, YoutubeSource)    =>
              Seq(Track(t.trackUrl, YoutubeSource, Some(t.name))) -> UntrackedTrack(t.tsInserted)
            case (t: UntrackedTrackRecord, SpotifySource)    =>
              Seq(Track(t.trackUrl, SpotifySource)) -> UntrackedTrack(t.tsInserted)
          }
          Chat(m.chatId) -> MediaGroup(tracks, m.name, m.url, m._id, mediaGroupType)
        }.groupMapReduce { case (k, _) => k } { case (_, v) => Seq(v) } { case (values1, values2) => values1 ++ values2 }
      _ <- tracksOrPlaylistsGroupedByChatId.map { case (Chat(chatId), playlists) =>
        sendTracksForSingleChat(chatId, playlists, "untracked")
          .handleError(e => log.info(s"failed to collect untracked tracks for chatId $chatId: ${e.getMessage}"))
      }.toList.sequence
      _ = log.info("finish collecting untracked tracks/playlists")
    } yield ()
  }

  private def sendTracksForSingleChat(chatId: String,
                                      mediaGroups: Seq[MediaGroup],
                                      songDirSuffix: String,
                                      chatSemaphore: IO[Semaphore[IO]] = Semaphore[IO](5),
                                     ): IO[Unit] = {
    for {
      s        <- chatSemaphore
      chatPath = s"$tracksDir/$chatId$songDirSuffix"
      _        <- mediaGroups
        .sortBy { v =>
          v.mediaGroupType match {
            case Tracked(_)                    => 0L
            case UntrackedTrack(tsInserted)    => tsInserted
            case UntrackedPlaylist(tsInserted) => tsInserted
          }
        }
        .traverse(p =>
          sendTracksForSingleChatFromSingleMediaGroup(
            chatId,
            p.groupName,
            p.groupUrl,
            p.tracks,
            p.mediaGroupType,
            p.recordId,
            chatPath,
            s,
          )
        )
    } yield ()
  }

  private def sendTracksForSingleChatFromSingleMediaGroup(chatId: String,
                                                          mediaName: String,
                                                          mediaUrl: String,
                                                          tracks: Seq[Track],
                                                          mediaGroupType: MediaGroupType,
                                                          recordId: String,
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
          bot.sendTracks(tracksGroup, mediaName, mediaUrl, chatId)
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
      sendTracksIO.map { _ =>
        mediaGroupType match {
          case Tracked(tsSaved)     =>
            persistenceClient.updateSaveTimeForTrackedPlaylist(recordId, tsSaved).unsafeRun()
          case UntrackedTrack(_)    =>
            persistenceClient.removeUntrackedTrack(recordId, chatId)
          case UntrackedPlaylist(_) =>
            persistenceClient.removeUntrackedPlaylist(recordId, chatId)
        }
      }
    }
  }
}
