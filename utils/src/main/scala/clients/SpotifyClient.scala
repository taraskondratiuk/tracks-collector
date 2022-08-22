package clients

import io.circe.parser.parse
import models.{Playlist, SpotifySource}
import scalaj.http.{Base64, Http, HttpResponse}
import validators.UrlValidator

import scala.annotation.tailrec

class SpotifyClient(spotifyClientId: String, spotifyClientSecret: String) extends UrlValidator {

  def getSpotifyTrackUrlsFromPlaylist(playlistId: String): Seq[String] = {
    val accessToken = getAccessToken
    getTrackUrlsFromPlaylist(accessToken, playlistId)
  }

  override def maybeExtractPlaylistFromUrl(url: String): Option[Playlist] = {
    val spotifyPlaylistIdPattern = """.*/playlist/(.*?)(\?.*)?""".r
    val maybePlaylistId = url match {
      case spotifyPlaylistIdPattern(playlistId, _) => Some(playlistId)
      case _                                       => None
    }
    maybePlaylistId
      .filter { pid =>
        getTrackUrlsFromPlaylist(getAccessToken, pid, onlyFirstPage = true)
        true
      }
      .map { pId =>
        Playlist(pId, url, SpotifySource)
      }
  }

  private def getAccessToken: String = {
    val response: HttpResponse[String] = Http("https://accounts.spotify.com/api/token")
      .header("Authorization", s"Basic ${Base64.encodeString(s"$spotifyClientId:$spotifyClientSecret")}")
      .postForm(Seq("grant_type" -> "client_credentials"))
      .asString

    parse(response.body).flatMap(json => json.hcursor.downField("access_token").as[String])
      .getOrElse(throw new Exception(s"failed to get token: $response"))
  }

  case class TracksPage(items: Seq[SpotifyItem], next: Option[String])

  case class SpotifyItem(track: Track)

  case class Track(external_urls: ExternalUrls)

  case class ExternalUrls(spotify: String)

  @tailrec
  private def getTrackUrlsFromPlaylist(accessToken: String,
                                       playlistId: String,
                                       pageUrl: Option[String] = None,
                                       acc: Seq[String] = Seq.empty,
                                       onlyFirstPage: Boolean = false,
                                      ): Seq[String] = {
    val url = pageUrl.getOrElse(s"https://api.spotify.com/v1/playlists/$playlistId/tracks")
    val response: HttpResponse[String] = Http(url)
      .header("Authorization", s"Bearer $accessToken")
      .asString
    import io.circe.generic.auto._
    val tracksPage = parse(response.body).flatMap(json => json.as[TracksPage])
      .fold(err => throw new Exception(s"failed to get tracks page, error: $err, response: $response"), page => page)
    val allTrackUrls = acc ++ tracksPage.items.map(_.track.external_urls.spotify)
    tracksPage.next match {
      case None               => allTrackUrls
      case _ if onlyFirstPage => allTrackUrls
      case Some(nextPage)     => getTrackUrlsFromPlaylist(accessToken, playlistId, Some(nextPage), allTrackUrls)
    }
  }
}
