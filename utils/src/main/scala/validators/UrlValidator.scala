package validators

import models.Playlist

trait UrlValidator {
  def maybeExtractPlaylistFromUrl(url: String): Option[Playlist]
}
