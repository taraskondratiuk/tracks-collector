package models

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

import java.io.File
import scala.annotation.tailrec

sealed trait TrackSource
case object SpotifySource extends TrackSource
case object YoutubeSource extends TrackSource

case class Playlist(playlistId: String, playlistUrl: String, source: TrackSource)

object Playlist {
  implicit val sourceDecoder: Decoder[TrackSource] = Decoder.decodeString.emap {
    case "SpotifySource" => Right(SpotifySource)
    case "YoutubeSource" => Right(YoutubeSource)
    case bad             => Left(s"unknown source: $bad")
  }
  implicit val sourceEncoder: Encoder[TrackSource] = Encoder.encodeString.contramap[TrackSource](_.toString)
  val playlistRecordDecoder: Decoder[PlaylistRecord] = deriveDecoder[PlaylistRecord]
  val playlistRecordEncoder: Encoder[PlaylistRecord] = deriveEncoder[PlaylistRecord]
}

case class PlaylistRecord(chatId: String,
                          playlistId: String,
                          playlistUrl: String,
                          source: TrackSource,
                          tsLastSave: Long,
                          _id: String,
                         ) {
  def toPlaylist: Playlist = {
    Playlist(playlistId, playlistUrl, source)
  }
}

object PlaylistRecord {
  def idFromPlaylistAndChatId(playlist: Playlist, chatId: String): String = {
    s"${playlist.playlistId}${playlist.source}$chatId"
  }

  def apply(playlist: Playlist, chatId: String, tsLastSave: Long = 0): PlaylistRecord = {
    PlaylistRecord(
      chatId,
      playlist.playlistId,
      playlist.playlistUrl,
      playlist.source,
      tsLastSave,
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
    val (validFiles, tooLargeFiles) = files.map(TrackFile(_)).partition(f => f.sizeMb < 50)

    @tailrec
    def groupTracks(tracks: List[TrackFile], acc: List[TrackFilesGroup] = List.empty): List[TrackFilesGroup] = {
      if (tracks.isEmpty) {
        acc
      } else {
        val (newGroup, unusedTracks) = tracks
          .tail
          .foldLeft(TrackFilesGroup(tracks.head) -> List.empty[TrackFile]) {
            case ((group, notUsedTracksAcc), trackToSend) =>
              group.flatMap(_.add(trackToSend)) match {
                case Some(updatedGroup) => Some(updatedGroup) -> notUsedTracksAcc
                case None               => group -> (notUsedTracksAcc :+ trackToSend)
              }
          }
        groupTracks(unusedTracks, acc ++ newGroup)
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
