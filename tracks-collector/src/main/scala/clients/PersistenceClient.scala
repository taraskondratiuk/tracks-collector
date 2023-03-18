package clients

import models.{Playlist, PlaylistRecord}

trait PersistenceClient {
  def addPlaylist(playlist: Playlist, chatId: String): Boolean

  def removePlaylist(playlist: Playlist, chatId: String): Boolean

  def listPlaylists(chatId: String): (Boolean, Seq[Playlist])

  def getAllPlaylistRecords(): Seq[PlaylistRecord]

  def updateSaveTimeForPlaylist(id: String, saveTime: Long): Unit
}
