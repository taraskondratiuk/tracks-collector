import io.circe.parser.parse
import io.circe.generic.auto._
import scalaj.http.{Base64, Http, HttpResponse}

import scala.annotation.tailrec

trait SpotifyClient {
  val SPOTIFY_CLIENT_ID: String
  val SPOTIFY_CLIENT_SECRET: String
  val SPOTIFY_PLAYLIST_ID: String

  private def getAccessToken: String = {
    val response: HttpResponse[String] = Http("https://accounts.spotify.com/api/token")
      .header("Authorization", s"Basic ${Base64.encodeString(s"$SPOTIFY_CLIENT_ID:$SPOTIFY_CLIENT_SECRET")}")
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
  private def getTrackUrlsFromPlaylist(accessToken: String, pageUrl: Option[String] = None, acc: Seq[String] = Seq.empty): Seq[String] = {
    val url = pageUrl.getOrElse(s"https://api.spotify.com/v1/playlists/$SPOTIFY_PLAYLIST_ID/tracks")
    val response: HttpResponse[String] = Http(url)
      .header("Authorization", s"Bearer $accessToken")
      .asString
    val tracksPage = parse(response.body).flatMap(json => json.as[TracksPage])
      .fold(err => throw new Exception(s"failed to get tracks page, error: $err, response: $response"), page => page)
    val allTrackUrls = acc ++ tracksPage.items.map(_.track.external_urls.spotify)
    tracksPage.next match {
      case None           => allTrackUrls
      case Some(nextPage) => getTrackUrlsFromPlaylist(accessToken, Some(nextPage), allTrackUrls)
    }
  }

  def getSpotifyTrackUrlsFromPlaylist: Seq[String] = {
    val accessToken = getAccessToken
    getTrackUrlsFromPlaylist(accessToken)
  }
}
