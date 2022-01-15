import io.circe.generic.auto._
import io.circe.parser.parse
import scalaj.http.{Http, HttpResponse}

import scala.annotation.tailrec

trait YoutubeClient {
  val YOUTUBE_API_KEY: String
  val YOUTUBE_PLAYLIST_ID: String

  case class PlaylistItemsPage(items: Seq[Item], nextPageToken: Option[String])
  case class Item(contentDetails: ContentDetails)
  case class ContentDetails(videoId: String)
  @tailrec
  private def getVideoUrlsFromPlaylist(pageToken: Option[String] = None, acc: Seq[String] = Seq.empty): Seq[String] = {
    val response: HttpResponse[String] = Http("https://www.googleapis.com/youtube/v3/playlistItems")
      .params(Map(
        "playlistId" -> YOUTUBE_PLAYLIST_ID,
        "key"        -> YOUTUBE_API_KEY,
        "part"       -> "contentDetails",
        "maxResults" -> "50",
        "pageToken"  -> pageToken.getOrElse(""),
      )).asString
    val playlistItemsPage = parse(response.body).flatMap(json => json.as[PlaylistItemsPage])
      .fold(err => throw new Exception(s"failed to get playlist items page, error: $err, response: $response"), page => page)
    val allVideoUrls = acc ++ playlistItemsPage.items.map(_.contentDetails.videoId).map(id => s"https://www.youtube.com/$id")
    playlistItemsPage.nextPageToken match {
      case None           => allVideoUrls
      case Some(nextPage) => getVideoUrlsFromPlaylist(Some(nextPage), allVideoUrls)
    }
  }

  def getYoutubeVideoUrlsFromPlaylist: Seq[String] = getVideoUrlsFromPlaylist()
}
