import org.scalatest.flatspec.AnyFlatSpec

class PersistenceClientSpec extends AnyFlatSpec {
  val persistenceClient: PersistenceClient = new PersistenceClient {
    override val PERSISTENCE_INFO_DIR: String = sys.env("PERSISTENCE_INFO_DIR")
  }
  val spotifyClient: SpotifyClient = new SpotifyClient {
    override val SPOTIFY_CLIENT_ID: String = sys.env("SPOTIFY_CLIENT_ID")
    override val SPOTIFY_CLIENT_SECRET: String = sys.env("SPOTIFY_CLIENT_SECRET")
    override val SPOTIFY_PLAYLIST_ID: String = sys.env("SPOTIFY_PLAYLIST_ID")
  }
  val youtubeClient: YoutubeClient = new YoutubeClient {
    override val YOUTUBE_API_KEY: String = sys.env("YOUTUBE_API_KEY")
    override val YOUTUBE_PLAYLIST_ID: String = sys.env("YOUTUBE_PLAYLIST_ID")
  }

  "persistence client" should "save tracks info" in {
    val allTracks = spotifyClient.getSpotifyTrackUrlsFromPlaylist ++ youtubeClient.getYoutubeVideoUrlsFromPlaylist
    persistenceClient.addUrlsToSave(allTracks)
  }
}
