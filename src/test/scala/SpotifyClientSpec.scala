import org.scalatest.flatspec.AnyFlatSpec

class SpotifyClientSpec extends AnyFlatSpec {
  val spotifyClient: SpotifyClient = new SpotifyClient {
    override val SPOTIFY_CLIENT_ID: String = sys.env("SPOTIFY_CLIENT_ID")
    override val SPOTIFY_CLIENT_SECRET: String = sys.env("SPOTIFY_CLIENT_SECRET")
    override val SPOTIFY_PLAYLIST_ID: String = sys.env("SPOTIFY_PLAYLIST_ID")
  }

  "spotify client" should "get track urls from playlist" in {
    val urls = spotifyClient.getSpotifyTrackUrlsFromPlaylist
    println(urls)
    println(urls.size)
  }
}
