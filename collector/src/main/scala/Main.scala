import clients.{PersistenceClient, SpotifyClient, YoutubeClient}
import models.{SpotifySource, YoutubeSource}

object Main {

  def main(args: Array[String]): Unit = {
    val spotifyClient = new SpotifyClient(sys.env("SPOTIFY_CLIENT_ID"), sys.env("SPOTIFY_CLIENT_SECRET"))
    val youtubeClient = new YoutubeClient(sys.env("YOUTUBE_API_KEY"))
    val persistenceClient = new PersistenceClient(sys.env("PERSISTENCE_DIR"))
    val chatIdsWithTracks = persistenceClient.getPlaylistsForAllChatIds.map { p =>
      val tracksUrls = p.source match {
        case SpotifySource =>
          spotifyClient.getSpotifyTrackUrlsFromPlaylist(p.playlistId)
        case YoutubeSource =>
          youtubeClient.getYoutubeVideoUrlsFromPlaylist(p.playlistId)
      }
      p.chatId -> tracksUrls
    }
      .groupMapReduce { case (chatId, _) => chatId } { case (_, urls) => urls } ((urls1, urls2) => urls1 ++ urls2)
      .toSeq

    chatIdsWithTracks.foreach { case (chatId, urls) =>
      persistenceClient.addUrlsToSave(chatId, urls)
    }
  }
}
