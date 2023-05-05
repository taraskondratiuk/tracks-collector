package clients

import cats.implicits.toTraverseOps
import clients.PersistenceClient.{DbErrorResponse, PersistenceClientResponse, SuccessfulResponse}
import com.typesafe.scalalogging.Logger
import io.circe.{Decoder, Encoder}
import io.circe.syntax._
import io.circe.parser.parse
import models.{Playlist, Track, TrackedPlaylistRecord, UntrackedPlaylistRecord, UntrackedTrackRecord}
import models.Playlist.{trackedPlaylistRecordDecoder, trackedPlaylistRecordEncoder, untrackedPlaylistRecordDecoder, untrackedPlaylistRecordEncoder, untrackedTrackRecordDecoder, untrackedTrackRecordEncoder}
import org.mongodb.scala.model.Aggregates.set
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.{Document, MongoClient, MongoCollection, model}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}

class MongoPersistenceClient(mongoUri: String) extends PersistenceClient {
  private val log                          = Logger(this.getClass.getSimpleName)
  private val mongoClient                  = MongoClient(s"mongodb://$mongoUri")
  private val db                           = mongoClient.getDatabase("tracks-collector-db")
  private val trackedPlaylistsCollection   = db.getCollection("tracked-playlists-collection")
  trackedPlaylistsCollection.createIndex(Document(""" { "chatId": 1 } """))
  private val untrackedTracksCollection    = db.getCollection("untracked-tracks-collection")
  untrackedTracksCollection.createIndex(Document(""" { "chatId": 1 } """))
  private val untrackedPlaylistsCollection = db.getCollection("untracked-playlists-collection")
  untrackedPlaylistsCollection.createIndex(Document(""" { "chatId": 1 } """))

  override def removeTrackedPlaylist(playlistNum: Int, chatId: String): PersistenceClientResponse[Option[Playlist]] = {
    for {
      playlists             <- listTrackedPlaylists(chatId)
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
              trackedPlaylistsCollection
                .deleteOne(equal("_id", TrackedPlaylistRecord.idFromPlaylistAndChatId(playlistToDelete, chatId)))
                .observable
                .toFuture()
            )
          } match {
            case Success(_) =>
              log.info(s"removed tracked playlist $playlistToDelete for chatId $chatId")
              SuccessfulResponse(Some(playlistToDelete))
            case Failure(e) =>
              log.warn(s"failed to remove tracked playlist $playlistToDelete for chatId $chatId: ${e.getMessage}")
              DbErrorResponse(e)
          }
      }
    } yield res
  }

  override def listTrackedPlaylists(chatId: String): PersistenceClientResponse[Seq[Playlist]] = {
    documentsToRecords[TrackedPlaylistRecord](
      await(
        trackedPlaylistsCollection
          .find(equal("chatId", chatId))
          .observable
          .toFuture()
      ),
      trackedPlaylistRecordDecoder,
    ) match {
      case Success(res) =>
        log.info(s"list tracked playlists for chatId $chatId")
        SuccessfulResponse(res.map(_.toPlaylist))
      case Failure(e)   =>
        log.warn(s"failed to list tracked playlists for chatId $chatId: ${e.getMessage}")
        DbErrorResponse(e)
    }
  }

  override def updateSaveTimeForTrackedPlaylist(id: String, saveTime: Long): PersistenceClientResponse[Unit] = {
    Try {
      await(
        trackedPlaylistsCollection
          .updateOne(equal("_id", id), set(model.Field("tsLastSave", saveTime)))
          .observable
          .toFuture()
      )
    } match {
      case Success(_) =>
        SuccessfulResponse(())
      case Failure(e) =>
        log.warn(s"failed to update tracked playlist with id $id: ${e.getMessage}")
        DbErrorResponse(e)
    }
  }

  override def addTrackedPlaylist(playlist: Playlist, chatId: String): PersistenceClientResponse[Unit] = {
    addRecord(
      playlist,
      "tracked playlist",
      chatId,
      TrackedPlaylistRecord.idFromPlaylistAndChatId,
      (obj: Playlist, chatId: String) => TrackedPlaylistRecord.apply(obj, chatId),
      trackedPlaylistRecordEncoder,
      trackedPlaylistsCollection,
    )
  }

  override def addUntrackedPlaylist(playlist: Playlist, chatId: String): PersistenceClientResponse[Unit] = {
    addRecord(
      playlist,
      "untracked playlist",
      chatId,
      UntrackedPlaylistRecord.idFromPlaylistAndChatId,
      UntrackedPlaylistRecord.apply,
      untrackedPlaylistRecordEncoder,
      untrackedPlaylistsCollection,
    )
  }

  override def addUntrackedTrack(track: Track, chatId: String): PersistenceClientResponse[Unit] = {
    addRecord(
      track,
      "untracked track",
      chatId,
      UntrackedTrackRecord.idFromTrackAndChatId,
      UntrackedTrackRecord.apply,
      untrackedTrackRecordEncoder,
      untrackedTracksCollection,
    )
  }

  override def getAllTrackedPlaylistRecords(): PersistenceClientResponse[Seq[TrackedPlaylistRecord]] = {
    getRecords("tracked playlists", trackedPlaylistRecordDecoder, trackedPlaylistsCollection)
  }

  override def getAllUntrackedPlaylistRecords(): PersistenceClientResponse[Seq[UntrackedPlaylistRecord]] = {
    getRecords("untracked playlists", untrackedPlaylistRecordDecoder, untrackedPlaylistsCollection)
  }

  override def getAllUntrackedTracksRecords(): PersistenceClientResponse[Seq[UntrackedTrackRecord]] = {
    getRecords("untracked tracks", untrackedTrackRecordDecoder, untrackedTracksCollection)
  }

  override def removeUntrackedTrack(_id: String, chatId: String): PersistenceClientResponse[Unit] = {
    removeRecord(_id, "untracked track", chatId, untrackedTracksCollection)
  }

  override def removeUntrackedPlaylist(_id: String, chatId: String): PersistenceClientResponse[Unit] = {
    removeRecord(_id, "untracked playlist", chatId, untrackedPlaylistsCollection)
  }

  private def removeRecord[T](_id: String,
                              recordType: String,
                              chatId: String,
                              collection: MongoCollection[Document],
                             ): PersistenceClientResponse[Unit] = {
    Try {
      await(
        collection
          .deleteOne(equal("_id", _id))
          .observable
          .toFuture()
      )
    } match {
      case Success(_) =>
        log.info(s"removed playlist $recordType for chatId $chatId")
        SuccessfulResponse(())
      case Failure(e) =>
        log.warn(s"failed to remove $recordType with id ${_id}: ${e.getMessage}")
        DbErrorResponse(e)
    }
  }

  private def addRecord[T, A](scalaObject: T,
                              recordType: String,
                              chatId: String,
                              _idFromObjectAndChatId: (T, String) => String,
                              recordFromObjectAndChatId: (T, String) => A,
                              encoder: Encoder[A],
                              collection: MongoCollection[Document],
                             ): PersistenceClientResponse[Unit] = {
    Try {
      val docs = await(
        collection
          .find(equal("_id", _idFromObjectAndChatId(scalaObject, chatId)))
          .observable.toFuture()
      )
      val recordNotExists = docs.isEmpty
      if (recordNotExists) {
        val record = recordFromObjectAndChatId(scalaObject, chatId)
        await(collection
          .insertOne(Document(record.asJson(encoder).toString()))
          .observable.toFuture()
        )
      }
    } match {
      case Success(_) =>
        log.info(s"saved $recordType $scalaObject for chatId $chatId")
        SuccessfulResponse(())
      case Failure(e) =>
        log.warn(s"failed to save $recordType $scalaObject for chatId $chatId: ${e.getMessage}")
        DbErrorResponse(e)
    }
  }

  private def getRecords[T](recordType: String,
                            decoder: Decoder[T],
                            collection: MongoCollection[Document],
                           ): PersistenceClientResponse[Seq[T]] = {
    documentsToRecords[T](
      await(collection.find().observable.toFuture()),
      decoder,
    ) match {
      case Success(v) =>
        SuccessfulResponse(v)
      case Failure(e) =>
        log.warn(s"failed to get all $recordType records: ${e.getMessage}")
        DbErrorResponse(e)
    }
  }

  private def documentsToRecords[T](documents: Seq[Document], d: Decoder[T]): Try[Seq[T]] = {
    documents.map { doc =>
      for {
        json   <- parse(doc.toJson())
        record <- json.as[T](d)
      } yield record
    }.sequence.toTry
  }

  private def await[T](f: => Future[T]): T = {
    Await.result(f, Duration.Inf)
  }
}
