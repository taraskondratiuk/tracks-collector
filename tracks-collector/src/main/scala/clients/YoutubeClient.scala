package clients

import io.circe.parser.parse
import models.{Playlist, Track, YoutubeSource}
import scalaj.http.{Http, HttpResponse}
import utils.DateUtil

import scala.annotation.tailrec

class YoutubeClient(youtubeApiKey: String) extends UrlValidator {

  private val YT_API_BASE_URI = "https://www.googleapis.com/youtube/v3"

  private case class YoutubeApiPageResponse(items: Seq[YtItem])

  private case class YtItem(snippet: Snippet)

  private case class PlaylistItemsPageResponse(items: Seq[YtVideo], nextPageToken: Option[String])

  private case class YtVideo(contentDetails: ContentDetails, snippet: Snippet)

  private case class ContentDetails(videoId: String)

  private case class Snippet(publishedAt: String,
                             videoOwnerChannelTitle: Option[String],
                             title: String,
                             channelTitle: Option[String],
                            )

  case class YoutubeTrackInfo(name: String, url: String)

  def getYoutubeTracksInfoFromPlaylist(playlistId: String, fromTs: Long): Seq[YoutubeTrackInfo] = {
    @tailrec
    def go(playlistId: String,
           fromTs: Long = 0,
           pageToken: Option[String] = None,
           acc: Seq[YoutubeTrackInfo] = Seq.empty,
          ): Seq[YoutubeTrackInfo] = {
      val resp: HttpResponse[String] = Http(s"$YT_API_BASE_URI/playlistItems")
        .params(Map(
          "playlistId" -> playlistId,
          "key"        -> youtubeApiKey,
          "part"       -> "contentDetails,snippet,status",
          "maxResults" -> "50",
          "pageToken"  -> pageToken.getOrElse(""),
        )).asString
      import io.circe.generic.auto._
      val playlistItemsPage = parse(resp.body).flatMap(json => json.as[PlaylistItemsPageResponse])
        .fold(err => throw new Exception(s"failed to get playlist items page, error: $err, response: $resp"), p => p)
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
        val resp: HttpResponse[String] = Http(s"$YT_API_BASE_URI/playlists")
          .params(Map(
            "id"   -> pId,
            "key"  -> youtubeApiKey,
            "part" -> "snippet",
          )).asString
        import io.circe.generic.auto._
        val playlistsPage = parse(resp.body).flatMap(json => json.as[YoutubeApiPageResponse])
          .fold(err => throw new Exception(s"failed to get playlists page, error: $err, response: $resp"), p => p)
        playlistsPage.items.headOption.map(p => Playlist(pId, url, p.snippet.title, YoutubeSource, 0L))
      }
  }

  override def maybeExtractTrackFromUrl(url: String): Option[Track] = {
    val ytTrackIdPattern1 = """.*v=(.*?)(&.*)?""".r
    val ytTrackIdPattern2 = """.+/(.*?)(\?.*)?$""".r
    val maybeTrackId = url match {
      case ytTrackIdPattern1(trackId, _) => Some(trackId)
      case ytTrackIdPattern2(trackId, _) => Some(trackId)
      case _                             => None
    }
    maybeTrackId
      .flatMap { trackId =>
        val resp: HttpResponse[String] = Http(s"$YT_API_BASE_URI/videos")
          .params(Map(
            "id"   -> trackId,
            "key"  -> youtubeApiKey,
            "part" -> "snippet",
          )).asString
        import io.circe.generic.auto._
        val tracksPage = parse(resp.body).flatMap(json => json.as[YoutubeApiPageResponse])
          .fold(err => throw new Exception(s"failed to get tracks page, error: $err, response: $resp"), p => p)
        tracksPage.items.headOption.map { v =>
          val channel = v.snippet.channelTitle.getOrElse("")
          val title = v.snippet.title
          val fullTitle = if (channel.endsWith(" - Topic")) { //YTMusic case
            s"${channel.replace(" - Topic", "")} - $title"
          } else title
          Track(trackId, url, fullTitle, YoutubeSource)
        }
      }
  }
}
