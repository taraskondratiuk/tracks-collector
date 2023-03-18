package models

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

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
