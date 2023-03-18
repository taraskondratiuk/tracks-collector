package clients

import io.circe.parser.parse
import models.{Playlist, YoutubeSource}
import scalaj.http.{Http, HttpResponse}
import utils.DateUtil
import validators.UrlValidator

import scala.annotation.tailrec

class YoutubeClient(youtubeApiKey: String) extends UrlValidator {

  def getYoutubeVideoUrlsFromPlaylist(playlistId: String, fromTs: Long): Seq[String] = {
    getVideoUrlsFromPlaylist(playlistId, fromTs)
  }

  override def maybeExtractPlaylistFromUrl(url: String): Option[Playlist] = {
    val ytPlaylistIdPattern = """.*list=(.*?)(&.*)?""".r
    val maybePlaylistId = url match {
      case ytPlaylistIdPattern(playlistId, _) => Some(playlistId)
      case _                                  => None
    }
    maybePlaylistId
      .filter { pid =>
        getVideoUrlsFromPlaylist(pid, onlyFirstPage = true)
        true
      }
      .map { pId =>
        Playlist(pId, url, YoutubeSource)
      }
  }

  case class PlaylistItemsPage(items: Seq[YtItem], nextPageToken: Option[String])

  case class YtItem(contentDetails: ContentDetails, snippet: Snippet)

  case class ContentDetails(videoId: String)

  case class Snippet(publishedAt: String)

  @tailrec
  private def getVideoUrlsFromPlaylist(playlistId: String,
                                       fromTs: Long = 0,
                                       pageToken: Option[String] = None,
                                       acc: Seq[String] = Seq.empty,
                                       onlyFirstPage: Boolean = false,
                                      ): Seq[String] = {
    val response: HttpResponse[String] = Http("https://www.googleapis.com/youtube/v3/playlistItems")
      .params(Map(
        "playlistId" -> playlistId,
        "key"        -> youtubeApiKey,
        "part"       -> "contentDetails,snippet,status",
        "maxResults" -> "50",
        "pageToken"  -> pageToken.getOrElse(""),
      )).asString
    import io.circe.generic.auto._
    val playlistItemsPage = parse(response.body).flatMap(json => json.as[PlaylistItemsPage])
      .fold(err => throw new Exception(s"failed to get playlist items page, error: $err, response: $response"), page => page)
    val allVideoUrls = acc ++ playlistItemsPage
      .items
      .filter(v => DateUtil.stringDateToEpochSecond(v.snippet.publishedAt) >= fromTs)
      .map(_.contentDetails.videoId)
      .map(id => s"https://www.youtube.com/watch?v=$id")
    playlistItemsPage.nextPageToken match {
      case None               => allVideoUrls
      case _ if onlyFirstPage => allVideoUrls
      case Some(nextPage)     => getVideoUrlsFromPlaylist(playlistId, fromTs, Some(nextPage), allVideoUrls)
    }
  }
}
