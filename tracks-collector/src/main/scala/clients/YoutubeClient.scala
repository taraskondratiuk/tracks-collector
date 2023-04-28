package clients

import io.circe.parser.parse
import models.{Playlist, YoutubeSource}
import scalaj.http.{Http, HttpResponse}
import utils.DateUtil
import validators.UrlValidator

import scala.annotation.tailrec

class YoutubeClient(youtubeApiKey: String) extends UrlValidator {

  private val YT_API_BASE_URI = "https://www.googleapis.com/youtube/v3"

  private case class PlaylistsPageResponse(items: Seq[YtPlaylist])

  private case class YtPlaylist(snippet: Snippet)

  private case class PlaylistItemsPageResponse(items: Seq[YtVideo], nextPageToken: Option[String])

  private case class YtVideo(contentDetails: ContentDetails, snippet: Snippet)

  private case class ContentDetails(videoId: String)

  private case class Snippet(publishedAt: String, videoOwnerChannelTitle: Option[String], title: String)

  case class YoutubeTrackInfo(name: String, url: String)

  def getYoutubeTracksInfoFromPlaylist(playlistId: String, fromTs: Long): Seq[YoutubeTrackInfo] = {
    @tailrec
    def go(playlistId: String,
           fromTs: Long = 0,
           pageToken: Option[String] = None,
           acc: Seq[YoutubeTrackInfo] = Seq.empty,
          ): Seq[YoutubeTrackInfo] = {
      val response: HttpResponse[String] = Http(s"$YT_API_BASE_URI/playlistItems")
        .params(Map(
          "playlistId" -> playlistId,
          "key"        -> youtubeApiKey,
          "part"       -> "contentDetails,snippet,status",
          "maxResults" -> "50",
          "pageToken"  -> pageToken.getOrElse(""),
        )).asString
      import io.circe.generic.auto._
      val playlistItemsPage = parse(response.body).flatMap(json => json.as[PlaylistItemsPageResponse])
        .fold(err => throw new Exception(s"failed to get playlist items page, error: $err, response: $response"), page => page)
      val allVideoUrls = acc ++ playlistItemsPage
        .items
        .filter(v => DateUtil.stringDateToEpochSecond(v.snippet.publishedAt) >= fromTs)
        .map { ytItem =>
          val channel = ytItem.snippet.videoOwnerChannelTitle.getOrElse("")
          val title = ytItem.snippet.title
          val fullTitle = if (channel.endsWith(" - Topic")) { //YTMusic case
            s"${channel.replace(" - Topic", "")} - $title"
          } else title
          YoutubeTrackInfo(fullTitle, s"https://www.youtube.com/watch?v=${ytItem.contentDetails.videoId}")
        }
      playlistItemsPage.nextPageToken match {
        case None           => allVideoUrls
        case Some(nextPage) => go(playlistId, fromTs, Some(nextPage), allVideoUrls)
      }
    }

    go(playlistId, fromTs)
  }

  override def maybeExtractPlaylistFromUrl(url: String): Option[Playlist] = {
    val ytPlaylistIdPattern = """.*list=(.*?)(&.*)?""".r
    val maybePlaylistId = url match {
      case ytPlaylistIdPattern(playlistId, _) => Some(playlistId)
      case _                                  => None
    }
    maybePlaylistId
      .flatMap { pId =>
        val response: HttpResponse[String] = Http(s"$YT_API_BASE_URI/playlists")
          .params(Map(
            "id"   -> pId,
            "key"  -> youtubeApiKey,
            "part" -> "snippet",
          )).asString
        import io.circe.generic.auto._
        val playlistsPage = parse(response.body).flatMap(json => json.as[PlaylistsPageResponse])
          .fold(err => throw new Exception(s"failed to get playlists page, error: $err, response: $response"), page => page)
        playlistsPage.items.headOption.map(p => Playlist(pId, url, p.snippet.title, YoutubeSource))
      }
  }
}
