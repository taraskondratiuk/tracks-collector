package clients

import cats.implicits.toTraverseOps
import com.typesafe.scalalogging.Logger
import io.circe.syntax._
import io.circe.parser.parse
import models.{Playlist, PlaylistRecord}
import models.Playlist.{playlistRecordDecoder, playlistRecordEncoder}
import org.mongodb.scala.model.Aggregates.set
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.{Document, MongoClient, model}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}

class MongoPersistenceClient(mongoUri: String) extends PersistenceClient {
  private val log         = Logger(this.getClass.getSimpleName)
  private val mongoClient = MongoClient(s"mongodb://$mongoUri")
  private val db          = mongoClient.getDatabase("tracks-collector-db")
  private val collection  = db.getCollection("tracks-collector-collection")

  override def addPlaylist(playlist: Playlist, chatId: String): Boolean = {
    Try {
      val docs = await(
        collection
          .find(equal("_id", PlaylistRecord.idFromPlaylistAndChatId(playlist, chatId)))
          .observable.toFuture()
      )
      val playlistNotExists = docs.isEmpty
      if (playlistNotExists) {
        val playlistRecord = PlaylistRecord(playlist, chatId)
        await(collection
          .insertOne(Document(playlistRecord.asJson(playlistRecordEncoder).toString()))
          .observable.toFuture()
        )
      }
    } match {
      case Success(_) =>
        log.info(s"saved playlist $playlist for chatId $chatId")
        true
      case Failure(e) =>
        log.warn(s"failed to save playlist $playlist for chatId $chatId: ${e.getMessage}")
        false
    }
  }

  override def removePlaylist(playlist: Playlist, chatId: String): Boolean = {
    Try {
      await(
        collection
          .deleteOne(equal("_id", PlaylistRecord.idFromPlaylistAndChatId(playlist, chatId)))
          .observable
          .toFuture()
      )
    } match {
      case Success(_) =>
        log.info(s"removed playlist $playlist for chatId $chatId")
        true
      case Failure(e) =>
        log.warn(s"failed to remove playlist $playlist for chatId $chatId: ${e.getMessage}")
        false
    }
  }

  override def listPlaylists(chatId: String): (Boolean, Seq[Playlist]) = {
    documentsToPlaylistRecords(
      await(
        collection
          .find(equal("chatId", chatId))
          .observable
          .toFuture()
      )
    ) match {
      case Success(res) =>
        log.info(s"list playlists for chatId $chatId")
        true -> res.map(_.toPlaylist)
      case Failure(e)   =>
        log.warn(s"failed to list playlists for chatId $chatId: ${e.getMessage}")
        false -> Seq.empty
    }
  }

  override def getAllPlaylistRecords(): Seq[PlaylistRecord] = {
    documentsToPlaylistRecords(
      await(
        collection
          .find()
          .observable
          .toFuture()
      )
    ) match {
      case Success(v) =>
        v
      case Failure(e) =>
        log.warn(s"failed to get all playlist records: ${e.getMessage}")
        throw e
    }
  }

  override def updateSaveTimeForPlaylist(id: String, saveTime: Long): Unit = {
    Try {
      await(
        collection
          .updateOne(equal("_id", id), set(model.Field("tsLastSave", saveTime)))
          .observable
          .toFuture()
      )
    } match {
      case Success(_) =>
        ()
      case Failure(e) =>
        log.warn(s"failed to update playlist with id $id: ${e.getMessage}")
        throw e
    }
  }

  private def documentsToPlaylistRecords(documents: Seq[Document]): Try[Seq[PlaylistRecord]] = {
    documents.map { doc =>
      for {
        json   <- parse(doc.toJson())
        record <- json.as[PlaylistRecord](playlistRecordDecoder)
      } yield record
    }.sequence.toTry
  }

  private def await[T](f: => Future[T]): T = {
    Await.result(f, Duration.Inf)
  }
}
