import scalaj.http._
import io.circe._
import io.circe.parser._

object Main extends SpotifyClient with YoutubeClient with PersistenceClient {
  override val SPOTIFY_CLIENT_ID: String = sys.env("SPOTIFY_CLIENT_ID")
  override val SPOTIFY_CLIENT_SECRET: String = sys.env("SPOTIFY_CLIENT_SECRET")
  override val SPOTIFY_PLAYLIST_ID: String = sys.env("SPOTIFY_PLAYLIST_ID")
  override val YOUTUBE_API_KEY: String = sys.env("YOUTUBE_API_KEY")
  override val YOUTUBE_PLAYLIST_ID: String = sys.env("YOUTUBE_PLAYLIST_ID")
  override val PERSISTENCE_INFO_DIR: String = sys.env("PERSISTENCE_INFO_DIR")

  def main(args: Array[String]): Unit = {
    val allTracks = getSpotifyTrackUrlsFromPlaylist ++ getYoutubeVideoUrlsFromPlaylist
    addUrlsToSave(allTracks)
  }
}
