package clients

import cats.implicits.toTraverseOps
import clients.PersistenceClient.{DbErrorResponse, PersistenceClientResponse, SuccessfulResponse}
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
  collection.createIndex(Document(""" { "chatId": 1 } """))

  override def addPlaylist(playlist: Playlist, chatId: String): PersistenceClientResponse[Unit] = {
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
        SuccessfulResponse(())
      case Failure(e) =>
        log.warn(s"failed to save playlist $playlist for chatId $chatId: ${e.getMessage}")
        DbErrorResponse(e)
    }
  }

  override def removePlaylist(playlistNum: Int, chatId: String): PersistenceClientResponse[Option[Playlist]] = {
    for {
      playlists             <- listPlaylists(chatId)
      maybePlaylistToRemove = playlists
        .sortBy(_.tsInserted)
        .zipWithIndex
        .find { case (_, idx) => idx + 1 == playlistNum }
      res                   <- maybePlaylistToRemove match {
        case None                        =>
          SuccessfulResponse(None)
        case Some((playlistToDelete, _)) =>
          Try {
            await(
              collection
                .deleteOne(equal("_id", PlaylistRecord.idFromPlaylistAndChatId(playlistToDelete, chatId)))
                .observable
                .toFuture()
            )
          } match {
            case Success(_) =>
              log.info(s"removed playlist $playlistToDelete for chatId $chatId")
              SuccessfulResponse(Some(playlistToDelete))
            case Failure(e) =>
              log.warn(s"failed to remove playlist $playlistToDelete for chatId $chatId: ${e.getMessage}")
              DbErrorResponse(e)
          }
      }
    } yield res
  }

  override def listPlaylists(chatId: String): PersistenceClientResponse[Seq[Playlist]] = {
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
        SuccessfulResponse(res.map(_.toPlaylist))
      case Failure(e)   =>
        log.warn(s"failed to list playlists for chatId $chatId: ${e.getMessage}")
        DbErrorResponse(e)
    }
  }

  override def getAllPlaylistRecords(): PersistenceClientResponse[Seq[PlaylistRecord]] = {
    documentsToPlaylistRecords(
      await(
        collection
          .find()
          .observable
          .toFuture()
      )
    ) match {
      case Success(v) =>
        SuccessfulResponse(v)
      case Failure(e) =>
        log.warn(s"failed to get all playlist records: ${e.getMessage}")
        DbErrorResponse(e)
    }
  }

  override def updateSaveTimeForPlaylist(id: String, saveTime: Long): PersistenceClientResponse[Unit] = {
    Try {
      await(
        collection
          .updateOne(equal("_id", id), set(model.Field("tsLastSave", saveTime)))
          .observable
          .toFuture()
      )
    } match {
      case Success(_) =>
        SuccessfulResponse(())
      case Failure(e) =>
        log.warn(s"failed to update playlist with id $id: ${e.getMessage}")
        DbErrorResponse(e)
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
