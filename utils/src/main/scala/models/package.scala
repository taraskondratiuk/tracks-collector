package models

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

sealed trait Source
case object SpotifySource extends Source
case object YoutubeSource extends Source

case class Playlist(playlistId: String, playlistUrl: String, source: Source)

object Playlist {
  implicit val sourceDecoder: Decoder[Source] = Decoder.decodeString.emap {
    case "SpotifySource" => Right(SpotifySource)
    case "YoutubeSource" => Right(YoutubeSource)
    case bad             => Left(s"unknown source: $bad")
  }
  implicit val sourceEncoder: Encoder[Source] = Encoder.encodeString.contramap[Source](_.toString)
  val playlistDecoder: Decoder[Playlist] = deriveDecoder[Playlist]
  val playlistEncoder: Encoder[Playlist] = deriveEncoder[Playlist]
}

case class PlaylistWithChatId(chatId: String, playlistId: String, playlistUrl: String, source: Source)

object PlaylistWithChatId {
  def apply(playlist: Playlist, chatId: String): PlaylistWithChatId = {
    PlaylistWithChatId(chatId, playlist.playlistId, playlist.playlistUrl, playlist.source)
  }
}
