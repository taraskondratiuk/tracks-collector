package models

import io.circe.generic.semiauto.{deriveCodec, deriveDecoder, deriveEncoder}
import io.circe.{Codec, Decoder, Encoder}

import java.io.File
import scala.collection.mutable

sealed trait TrackSource
case object SpotifySource extends TrackSource
case object YoutubeSource extends TrackSource

case class Track(trackId: String, trackUrl: String, name: String, source: TrackSource)

case class Playlist(playlistId: String, playlistUrl: String, name: String, source: TrackSource, tsInserted: Long)

object Playlist {
  implicit val sourceCodec: Codec[TrackSource] = deriveCodec[TrackSource]
  val trackedPlaylistRecordDecoder: Decoder[TrackedPlaylistRecord] = deriveDecoder[TrackedPlaylistRecord]
  val trackedPlaylistRecordEncoder: Encoder[TrackedPlaylistRecord] = deriveEncoder[TrackedPlaylistRecord]
  val untrackedPlaylistRecordDecoder: Decoder[UntrackedPlaylistRecord] = deriveDecoder[UntrackedPlaylistRecord]
  val untrackedPlaylistRecordEncoder: Encoder[UntrackedPlaylistRecord] = deriveEncoder[UntrackedPlaylistRecord]
  val untrackedTrackRecordDecoder: Decoder[UntrackedTrackRecord] = deriveDecoder[UntrackedTrackRecord]
  val untrackedTrackRecordEncoder: Encoder[UntrackedTrackRecord] = deriveEncoder[UntrackedTrackRecord]
}

sealed trait UntrackedMedia {
  def chatId: String
  def source: TrackSource
  def url: String
  def name: String
  def _id: String
  def tsInserted: Long
}

case class UntrackedPlaylistRecord(chatId: String,
                                   playlistId: String,
                                   playlistUrl: String,
                                   name: String,
                                   source: TrackSource,
                                   tsInserted: Long,
                                   _id: String,
                                  ) extends UntrackedMedia {
  def toPlaylist: Playlist = {
    Playlist(playlistId, playlistUrl, name, source, tsInserted)
  }

  override def url: String = playlistUrl
}

object UntrackedPlaylistRecord {
  def idFromPlaylistAndChatId(playlist: Playlist, chatId: String): String = {
    s"${playlist.playlistId}${playlist.source}$chatId"
  }

  def apply(playlist: Playlist, chatId: String): UntrackedPlaylistRecord = {
    UntrackedPlaylistRecord(
      chatId,
      playlist.playlistId,
      playlist.playlistUrl,
      playlist.name,
      playlist.source,
      System.currentTimeMillis(),
      idFromPlaylistAndChatId(playlist, chatId),
    )
  }
}

case class UntrackedTrackRecord(chatId: String,
                                trackId: String,
                                trackUrl: String,
                                name: String,
                                source: TrackSource,
                                tsInserted: Long,
                                _id: String,
                               ) extends UntrackedMedia {
  override def url: String = trackUrl
}

object UntrackedTrackRecord {
  def idFromTrackAndChatId(track: Track, chatId: String): String = {
    s"${track.trackId}${track.source}$chatId"
  }

  def apply(track: Track, chatId: String): UntrackedTrackRecord = {
    UntrackedTrackRecord(
      chatId,
      track.trackId,
      track.trackUrl,
      track.name,
      track.source,
      System.currentTimeMillis(),
      idFromTrackAndChatId(track, chatId),
    )
  }
}

case class TrackedPlaylistRecord(chatId: String,
                                 playlistId: String,
                                 playlistUrl: String,
                                 name: String,
                                 source: TrackSource,
                                 tsLastSave: Long,
                                 tsInserted: Long,
                                 _id: String,
                         ) {
  def toPlaylist: Playlist = {
    Playlist(playlistId, playlistUrl, name, source, tsInserted)
  }
}

object TrackedPlaylistRecord {
  def idFromPlaylistAndChatId(playlist: Playlist, chatId: String): String = {
    s"${playlist.playlistId}${playlist.source}$chatId"
  }

  def apply(playlist: Playlist, chatId: String, tsLastSave: Long = 0): TrackedPlaylistRecord = {
    TrackedPlaylistRecord(
      chatId,
      playlist.playlistId,
      playlist.playlistUrl,
      playlist.name,
      playlist.source,
      tsLastSave,
      System.currentTimeMillis(),
      idFromPlaylistAndChatId(playlist, chatId),
    )
  }
}

case class TrackFile(file: File, sizeMb: Long, name: String) {
  override def toString: String = {
    name
  }
}

object TrackFile {
  def apply(file: File): TrackFile = {
    TrackFile(file, (file.length() / (1024 * 1024)) + 1, file.getName)
  }
}

sealed trait TrackFilesGroup {
  def totalTracks: Int
  def totalSizeMb: Long

  //tg bot limit is 50mb per message and 10 media files per msg
  def canBeAdded(newTrack: TrackFile): Boolean = {
    (totalSizeMb + newTrack.sizeMb < 50) && (totalTracks < 10)
  }

  def add(newTrack: TrackFile): Option[TrackFilesGroup]
}

object TrackFilesGroup {
  def generateGroupsFromFiles(files: Seq[File]): List[TrackFilesGroup] = {
    val (validFiles, tooLargeFiles) = files
      .sortBy(f => f.lastModified())
      .map(TrackFile(_))
      .partition(f => f.sizeMb < 50)

    def groupTracks(tracks: List[TrackFile]): List[TrackFilesGroup] = {
      if (tracks.isEmpty) {
        List.empty
      } else {
        val acc: mutable.ListBuffer[TrackFilesGroup] = mutable.ListBuffer.empty
        var groupAcc = TrackFilesGroup(tracks.head)
        tracks.tail.foreach { trackFile =>
          groupAcc.foreach { grp =>
            grp.add(trackFile) match {
              case Some(updatedGroup) =>
                groupAcc = Some(updatedGroup)
              case None               =>
                acc.addAll(groupAcc)
                groupAcc = TrackFilesGroup(trackFile)
            }
          }
        }
        acc.addAll(groupAcc)
        acc.toList
      }
    }

    groupTracks(validFiles.toList) ++ TrackFilesGroup(tooLargeFiles: _*)
  }

  private def apply(tracks: TrackFile*): Option[TrackFilesGroup] = {
    if (tracks.isEmpty) {
      None
    } else if (tracks.map(_.sizeMb).sum >= 50) {
      Some(TrackFilesInvalidGroup(tracks.toList))
    } else if (tracks.size == 1) {
      Some(TrackFilesValidSingleElement(tracks.head))
    } else {
      Some(TrackFilesValidGroup(tracks.toList))
    }
  }

  case class TrackFilesValidGroup(tracks: List[TrackFile]) extends TrackFilesGroup {
    override def add(newTrack: TrackFile): Option[TrackFilesGroup] = {
      if (canBeAdded(newTrack)) TrackFilesGroup((tracks :+ newTrack): _*) else None
    }

    override def totalTracks: Int = tracks.size

    override def totalSizeMb: Long = tracks.map(_.sizeMb).sum
  }

  case class TrackFilesValidSingleElement(track: TrackFile) extends TrackFilesGroup {
    override def add(newTrack: TrackFile): Option[TrackFilesGroup] = {
      if (canBeAdded(newTrack)) TrackFilesGroup(track, newTrack) else None
    }

    override def totalTracks: Int = 1

    override def totalSizeMb: Long = track.sizeMb
  }

  case class TrackFilesInvalidGroup(tracks: List[TrackFile]) extends TrackFilesGroup {
    override def totalTracks: Int = tracks.size

    override def totalSizeMb: Long = tracks.map(_.sizeMb).sum

    override def add(newTrack: TrackFile): Option[TrackFilesGroup] = None
  }
}
