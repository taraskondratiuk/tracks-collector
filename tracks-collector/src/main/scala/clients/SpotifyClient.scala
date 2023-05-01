package clients

import io.circe.parser.parse
import models.{Playlist, SpotifySource}
import scalaj.http.{Base64, Http, HttpResponse}
import utils.DateUtil
import validators.UrlValidator

import scala.annotation.tailrec

class SpotifyClient(spotifyClientId: String, spotifyClientSecret: String) extends UrlValidator {

  private val SPOTIFY_API_BASE_URI = "https://api.spotify.com/v1/playlists"

  private case class PlaylistResponse(name: String)

  private case class TracksPageResponse(items: Seq[SpotifyItem], next: Option[String])

  private case class SpotifyItem(track: Track, added_at: String)

  private case class Track(external_urls: ExternalUrls)

  private case class ExternalUrls(spotify: String)

  def getSpotifyTrackUrlsFromPlaylist(playlistId: String, fromTs: Long): Seq[String] = {
    val accessToken = getAccessToken

    @tailrec
    def go(accessToken: String,
           playlistId: String,
           fromTs: Long = 0,
           pageUrl: Option[String] = None,
           acc: Seq[String] = Seq.empty,
          ): Seq[String] = {
      val url = pageUrl.getOrElse(s"$SPOTIFY_API_BASE_URI/$playlistId/tracks")
      val response: HttpResponse[String] = Http(url)
        .header("Authorization", s"Bearer $accessToken")
        .asString
      import io.circe.generic.auto._
      val tracksPage = parse(response.body).flatMap(json => json.as[TracksPageResponse])
        .fold(err => throw new Exception(s"failed to get tracks page, error: $err, response: $response"), page => page)
      val allTrackUrls = acc ++ tracksPage
        .items
        .filter(v => DateUtil.stringDateToEpochSecond(v.added_at) >= fromTs)
        .map(_.track.external_urls.spotify)
      tracksPage.next match {
        case None           => allTrackUrls
        case Some(nextPage) => go(accessToken, playlistId, fromTs, Some(nextPage), allTrackUrls)
      }
    }

    go(accessToken, playlistId, fromTs)
  }

  override def maybeExtractPlaylistFromUrl(url: String): Option[Playlist] = {
    val spotifyPlaylistIdPattern = """.*/playlist/(.*?)(\?.*)?""".r
    val maybePlaylistId = url match {
      case spotifyPlaylistIdPattern(playlistId, _) => Some(playlistId)
      case _                                       => None
    }
    val accessToken = getAccessToken
    maybePlaylistId
      .flatMap { pId =>
        val response: HttpResponse[String] = Http(s"$SPOTIFY_API_BASE_URI/$pId")
          .header("Authorization", s"Bearer $accessToken")
          .asString
        import io.circe.generic.auto._
        if (response.code != 404) {
          val playlist = parse(response.body).flatMap(json => json.as[PlaylistResponse])
            .fold(err => throw new Exception(s"failed to get playlist page, error: $err, response: $response"), page => page)
          Some(Playlist(pId, url, playlist.name, SpotifySource, 0L))
        } else {
          None
        }
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
}
