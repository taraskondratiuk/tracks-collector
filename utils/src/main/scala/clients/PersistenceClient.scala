package clients

import io.circe.syntax._
import io.circe.parser._
import models.{Playlist, PlaylistWithChatId}
import models.Playlist.{playlistDecoder, playlistEncoder}

import java.io.{BufferedReader, BufferedWriter, File, FileReader, FileWriter}
import java.time.format.DateTimeFormatter
import java.util.stream.Collectors
import scala.io.Source

class PersistenceClient(persistenceInfoDir: String) {

  def getAlreadySavedUrls(chatId: String): Seq[String] = {
    val dir =  new File(chatSubdir(persistenceInfoDir, chatId))

    if (dir.exists() && dir.isDirectory) {
      dir.listFiles().filter(_.isFile).toSeq.flatMap { file =>
        val src = Source.fromFile(file)
        val urls = src.getLines().toSeq
        src.close()
        urls
      }
    } else Seq.empty
  }

  def addUrlsToSave(chatId: String, allUrls: Seq[String]): Unit = {
    val newUrls = allUrls diff getAlreadySavedUrls(chatId)
    if (newUrls.nonEmpty) {
      val dtFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss")
      val file = new File(s"$persistenceInfoDir/${java.time.LocalDateTime.now().format(dtFormatter)}.txt")
      val bw = new BufferedWriter(new FileWriter(file))
      bw.write(newUrls.mkString("\n"))
      bw.close()
    }
  }

  def addPlaylist(playlist: Playlist, chatId: String): Unit = {
    val file = new File(playlistFile(persistenceInfoDir, chatId))
    file.getParentFile.mkdirs()

    val playlists = getPlaylistsFromFile(file)
    if (!playlists.map(_.playlistId).contains(playlist.playlistId)) {
      val bw = new BufferedWriter(new FileWriter(file, true))
      bw.write(s"${playlist.asJson(playlistEncoder).noSpaces}\n")
      bw.close()
    }
  }

  def removePlaylist(playlist: Playlist, chatId: String): Unit = {
    val file = new File(playlistFile(persistenceInfoDir, chatId))
    file.getParentFile.mkdirs()

    val resFileContent = getPlaylistsFromFile(file)
      .filter(p => !p.playlistId.equals(playlist.playlistId))
      .map(_.asJson(playlistEncoder).noSpaces)
      .mkString("\n")

    val bw = new BufferedWriter(new FileWriter(file, false))
    bw.write(resFileContent)
    bw.close()
  }

  def listPlaylists(chatId: String): Seq[Playlist] = {
    val file = new File(playlistFile(persistenceInfoDir, chatId))
    file.getParentFile.mkdirs()

    getPlaylistsFromFile(file)
  }

  def getPlaylistsFromFile(file: File): Seq[Playlist] = {
    if (file.exists()) {


      val reader = new BufferedReader(new FileReader(file))
      import scala.jdk.CollectionConverters._
      val fileLines = reader.lines().collect(Collectors.toList[String]).asScala
      reader.close()

      fileLines
        .map { s =>
          parse(s)
            .flatMap(json => json.as[Playlist](playlistDecoder))
            .toTry
            .fold(e => throw new Exception(s"failed to decode playlist $s for fle ${file.getPath}", e), res => res)
        }
        .toSeq
    } else Seq.empty
  }

  def getPlaylistsForAllChatIds: Seq[PlaylistWithChatId] = {
    new File(persistenceInfoDir)
      .listFiles()
      .filter(_.isDirectory)
      .map(_.getName)
      .toSeq
      .flatMap { chatId =>
        listPlaylists(chatId).map(p => PlaylistWithChatId(p, chatId))
      }
  }

  def playlistFile(persistenceInfoDir: String, chatId: String): String = {
    s"${chatSubdir(persistenceInfoDir, chatId)}/playlist.txt"
  }

  def chatSubdir(persistenceInfoDir: String, chatId: String): String = {
    s"$persistenceInfoDir/$chatId"
  }

  def getChatIdFromTrackPath(trackPath: String): String = {
    ???
  }
}
