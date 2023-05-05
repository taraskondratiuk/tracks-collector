package clients

import clients.PersistenceClient.PersistenceClientResponse
import models.{Playlist, Track, TrackedPlaylistRecord, UntrackedPlaylistRecord, UntrackedTrackRecord}

trait PersistenceClient {

  def addTrackedPlaylist(playlist: Playlist, chatId: String): PersistenceClientResponse[Unit]

  def addUntrackedPlaylist(playlist: Playlist, chatId: String): PersistenceClientResponse[Unit]

  def addUntrackedTrack(track: Track, chatId: String): PersistenceClientResponse[Unit]

  def removeTrackedPlaylist(playlistNum: Int, chatId: String): PersistenceClientResponse[Option[Playlist]]

  def listTrackedPlaylists(chatId: String): PersistenceClientResponse[Seq[Playlist]]

  def getAllUntrackedPlaylistRecords(): PersistenceClientResponse[Seq[UntrackedPlaylistRecord]]

  def getAllUntrackedTracksRecords(): PersistenceClientResponse[Seq[UntrackedTrackRecord]]

  def removeUntrackedTrack(_id: String, chatId: String): PersistenceClientResponse[Unit]

  def removeUntrackedPlaylist(_id: String, chatId: String): PersistenceClientResponse[Unit]

  def getAllTrackedPlaylistRecords(): PersistenceClientResponse[Seq[TrackedPlaylistRecord]]

  def updateSaveTimeForTrackedPlaylist(id: String, saveTime: Long): PersistenceClientResponse[Unit]
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
