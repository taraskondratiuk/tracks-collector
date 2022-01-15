import org.scalatest.flatspec.AnyFlatSpec

class YoutubeClientSpec extends AnyFlatSpec {
  val youtubeClient: YoutubeClient = new YoutubeClient {
    override val YOUTUBE_API_KEY: String = sys.env("YOUTUBE_API_KEY")
    override val YOUTUBE_PLAYLIST_ID: String = sys.env("YOUTUBE_PLAYLIST_ID")
  }

  "youtube client" should "get video urls from playlist" in {
    val urls = youtubeClient.getYoutubeVideoUrlsFromPlaylist
    println(urls)
    println(urls.size)
  }
}
