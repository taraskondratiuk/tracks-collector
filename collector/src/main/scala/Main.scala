import clients.{PersistenceClient, SpotifyClient, YoutubeClient}

object Main {

  def main(args: Array[String]): Unit = {
    val spotifyClient = new SpotifyClient(sys.env("SPOTIFY_CLIENT_ID"), sys.env("SPOTIFY_CLIENT_SECRET"))
    val youtubeClient = new YoutubeClient(sys.env("YOUTUBE_API_KEY"))
    val persistenceClient = new PersistenceClient(sys.env("SONG_INFO_DIR"))
    val chatIdsWithTracks = persistenceClient.getPlaylistsForAllChatIds.map { p =>
      p.chatId -> (spotifyClient.getSpotifyTrackUrlsFromPlaylist(p.playlistId) ++
        youtubeClient.getYoutubeVideoUrlsFromPlaylist(p.playlistId))
    }
    chatIdsWithTracks.foreach { case (chatId, urls) =>
      persistenceClient.addUrlsToSave(chatId, urls)
    }
  }
}
