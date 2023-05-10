package clients

import models.{Playlist, Track}

trait UrlValidator {
  def maybeExtractPlaylistFromUrl(url: String): Option[Playlist]

  def maybeExtractTrackFromUrl(url: String): Option[Track]
}
