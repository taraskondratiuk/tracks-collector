package clients

import io.circe.parser.parse
import models.{Playlist, YoutubeSource}
import scalaj.http.{Http, HttpResponse}
import utils.DateUtil
import validators.UrlValidator

import scala.annotation.tailrec

class YoutubeClient(youtubeApiKey: String) extends UrlValidator {

  def getYoutubeTracksInfoFromPlaylist(playlistId: String, fromTs: Long): Seq[YoutubeTrackInfo] = {
    getTracksInfoFromPlaylist(playlistId, fromTs)
  }

  override def maybeExtractPlaylistFromUrl(url: String): Option[Playlist] = {
    val ytPlaylistIdPattern = """.*list=(.*?)(&.*)?""".r
    val maybePlaylistId = url match {
      case ytPlaylistIdPattern(playlistId, _) => Some(playlistId)
      case _                                  => None
    }
    maybePlaylistId
      .filter { pid =>
        getTracksInfoFromPlaylist(pid, onlyFirstPage = true)
        true
      }
      .map { pId =>
        Playlist(pId, url, YoutubeSource)
      }
  }

  private case class PlaylistItemsPage(items: Seq[YtItem], nextPageToken: Option[String])

  private case class YtItem(contentDetails: ContentDetails, snippet: Snippet)

  private case class ContentDetails(videoId: String)

  private case class Snippet(publishedAt: String, videoOwnerChannelTitle: Option[String], title: String)

  case class YoutubeTrackInfo(name: String, url: String)

  @tailrec
  private def getTracksInfoFromPlaylist(playlistId: String,
                                       fromTs: Long = 0,
                                       pageToken: Option[String] = None,
                                       acc: Seq[YoutubeTrackInfo] = Seq.empty,
                                       onlyFirstPage: Boolean = false,
                                      ): Seq[YoutubeTrackInfo] = {
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
      .map { ytItem =>
        val channel = ytItem.snippet.videoOwnerChannelTitle.getOrElse("")
        val title = ytItem.snippet.title
        val fullTitle = if (channel.endsWith(" - Topic")) { //YTMusic case
          s"${channel.replace(" - Topic", "")} - $title"
        } else title
        YoutubeTrackInfo(fullTitle, s"https://www.youtube.com/watch?v=${ytItem.contentDetails.videoId}")
      }
    playlistItemsPage.nextPageToken match {
      case None               => allVideoUrls
      case _ if onlyFirstPage => allVideoUrls
      case Some(nextPage)     => getTracksInfoFromPlaylist(playlistId, fromTs, Some(nextPage), allVideoUrls)
    }
  }
}
