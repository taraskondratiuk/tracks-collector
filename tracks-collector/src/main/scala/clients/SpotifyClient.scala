package clients

import io.circe.parser.parse
import models.{Playlist, SpotifySource, Track}
import scalaj.http.{Base64, Http, HttpResponse}
import utils.DateUtil

import scala.annotation.tailrec

class SpotifyClient(spotifyClientId: String, spotifyClientSecret: String) extends UrlValidator {

  private val SPOTIFY_API_BASE_URI = "https://api.spotify.com/v1"

  private case class PlaylistResponse(name: String)

  private case class TracksPageResponse(items: Seq[SpotifyItem], next: Option[String])

  private case class SpotifyItem(track: SpotifyTrack, added_at: String)

  private case class SpotifyTrack(external_urls: ExternalUrls)

  private case class ExternalUrls(spotify: String)

  private case class TrackResponse(name: String, album: Album)

  private case class Album(artists: Seq[Artist])

  private case class Artist(name: String)

  def getSpotifyTrackUrlsFromPlaylist(playlistId: String, fromTs: Long): Seq[String] = {
    val accessToken = getAccessToken

    @tailrec
    def go(accessToken: String,
           playlistId: String,
           fromTs: Long = 0,
           pageUrl: Option[String] = None,
           acc: Seq[String] = Seq.empty,
          ): Seq[String] = {
      val url = pageUrl.getOrElse(s"$SPOTIFY_API_BASE_URI/playlists/$playlistId/tracks")
      val resp: HttpResponse[String] = Http(url)
        .header("Authorization", s"Bearer $accessToken")
        .asString
      import io.circe.generic.auto._
      val tracksPage = parse(resp.body).flatMap(json => json.as[TracksPageResponse])
        .fold(err => throw new Exception(s"failed to get tracks page, error: $err, response: $resp"), p => p)
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
        val resp: HttpResponse[String] = Http(s"$SPOTIFY_API_BASE_URI/playlists/$pId")
          .header("Authorization", s"Bearer $accessToken")
          .asString
        import io.circe.generic.auto._
        if (resp.code < 400) {
          val playlist = parse(resp.body).flatMap(json => json.as[PlaylistResponse])
            .fold(err => throw new Exception(s"failed to get playlist page, error: $err, response: $resp"), p => p)
          Some(Playlist(pId, url, playlist.name, SpotifySource, 0L))
        } else {
          None
        }
      }
  }

  override def maybeExtractTrackFromUrl(url: String): Option[Track] = {
    val spotifyTrackIdPattern = """.*/track/(.*?)(\?.*)?""".r
    val maybeTrackId = url match {
      case spotifyTrackIdPattern(trackId, _) => Some(trackId)
      case _                                 => None
    }
    val accessToken = getAccessToken
    maybeTrackId
      .flatMap { trackId =>
        val resp: HttpResponse[String] = Http(s"$SPOTIFY_API_BASE_URI/$trackId")
          .header("Authorization", s"Bearer $accessToken")
          .asString
        import io.circe.generic.auto._
        if (resp.code < 400) {
          val track = parse(resp.body).flatMap(json => json.as[TrackResponse])
            .fold(err => throw new Exception(s"failed to get track page, error: $err, response: $resp"), p => p)
          Some(Track(trackId, url, s"${track.album.artists.map(_.name).mkString(", ")} - ${track.name}", SpotifySource))
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
