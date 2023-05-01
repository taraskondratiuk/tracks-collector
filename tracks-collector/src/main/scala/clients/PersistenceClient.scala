package clients

import clients.PersistenceClient.PersistenceClientResponse
import models.{Playlist, PlaylistRecord}

trait PersistenceClient {

  def addPlaylist(playlist: Playlist, chatId: String): PersistenceClientResponse[Unit]

  def removePlaylist(playlistNum: Int, chatId: String): PersistenceClientResponse[Option[Playlist]]

  def listPlaylists(chatId: String): PersistenceClientResponse[Seq[Playlist]]

  def getAllPlaylistRecords(): PersistenceClientResponse[Seq[PlaylistRecord]]

  def updateSaveTimeForPlaylist(id: String, saveTime: Long): PersistenceClientResponse[Unit]
}

object PersistenceClient {
  sealed trait PersistenceClientResponse[+A] {
    def unsafeRun(): A

    def map[B](f: A => B): PersistenceClientResponse[B] = {
      this match {
        case v: DbErrorResponse => v
        case v                  => SuccessfulResponse(f(v.unsafeRun()))
      }
    }
    def flatMap[B](f: A => PersistenceClientResponse[B]): PersistenceClientResponse[B] = {
      this match {
        case v: DbErrorResponse => v
        case v                  => f(v.unsafeRun())
      }
    }
  }

  case class SuccessfulResponse[+A](response: A) extends PersistenceClientResponse[A] {
    override def unsafeRun(): A = response
  }

  case class DbErrorResponse(err: Throwable) extends PersistenceClientResponse[Nothing] {
    override def unsafeRun(): Nothing = throw err
  }
}
