import clients.{PersistenceClient, SpotifyClient, YoutubeClient}
import io.circe.syntax.EncoderOps
import models.{Playlist, SpotifySource}
import models.Playlist.{playlistDecoder, playlistEncoder}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.{BufferedReader, File, FileReader}
import java.util.stream.Collectors

class PersistenceClientSpec extends AnyFlatSpec with Matchers {
  val persistenceDir = "/tmp/prekol" //sys.env("PERSISTENCE_INFO_DIR")
  val persistenceClient = new PersistenceClient(persistenceDir)

  "persistence client" should "add playlist" in {
    val chatId = "chatId"
    val playlist1 = Playlist("playlistId1", "url1", SpotifySource)
    val playlist2 = Playlist("playlistId2", "url2", SpotifySource)
    persistenceClient.addPlaylist(playlist1, chatId)
    persistenceClient.addPlaylist(playlist1, chatId)
    persistenceClient.addPlaylist(playlist2, chatId)

    val dirPath = persistenceClient.chatSubdir(persistenceDir, chatId)
    val filePath = persistenceClient.playlistFile(persistenceDir, chatId)
    val reader = new BufferedReader(new FileReader(filePath))
    import scala.jdk.CollectionConverters._
    val fileContent = reader.lines().collect(Collectors.toList[String]).asScala.mkString("\n")
    reader.close()

    fileContent.shouldBe(s"${playlist1.asJson(playlistEncoder).noSpaces}\n${playlist2.asJson(playlistEncoder).noSpaces}")

    new File(filePath).delete()
    new File(dirPath).delete()
  }

  "persistence client" should "remove playlist" in {
    val chatId = "chatId"
    val playlist1 = Playlist("playlistId1", "url1", SpotifySource)
    val playlist2 = Playlist("playlistId2", "url2", SpotifySource)
    val playlist3 = Playlist("playlistId3", "url3", SpotifySource)
    persistenceClient.addPlaylist(playlist1, chatId)
    persistenceClient.addPlaylist(playlist2, chatId)
    persistenceClient.addPlaylist(playlist3, chatId)

    persistenceClient.removePlaylist(playlist2, chatId)

    val dirPath = persistenceClient.chatSubdir(persistenceDir, chatId)
    val filePath = persistenceClient.playlistFile(persistenceDir, chatId)
    val reader = new BufferedReader(new FileReader(filePath))
    import scala.jdk.CollectionConverters._
    val fileContent = reader.lines().collect(Collectors.toList[String]).asScala.mkString("\n")
    reader.close()

    fileContent.shouldBe(s"${playlist1.asJson(playlistEncoder).noSpaces}\n${playlist3.asJson(playlistEncoder).noSpaces}")

    new File(filePath).delete()
    new File(dirPath).delete()
  }

  "persistence client" should "save tracks info" in {
    val chatId = "123"
    val urls1 = Seq("1", "2")
    val urls2 = Seq("1", "3")

    persistenceClient.addUrlsToSave(chatId, urls1)
    persistenceClient.addUrlsToSave(chatId, urls2)
    persistenceClient.addUrlsToSave(chatId, urls1)

    val dirPath = persistenceClient.chatSubdir(persistenceDir, chatId)
    val filePaths = new File(dirPath).listFiles().sortBy(_.getPath)
    filePaths.length shouldBe 2
    filePaths.zipWithIndex.map { case (file, idx) =>
      val reader = new BufferedReader(new FileReader(file))
      import scala.jdk.CollectionConverters._
      val fileContent = reader.lines().collect(Collectors.toList[String]).asScala.sorted.mkString("\n")
      reader.close()

      if (idx == 0) {
        fileContent.shouldBe("1\n2")
      } else {
        fileContent.shouldBe("3")
      }
    }

    filePaths.foreach(_.delete())
    new File(dirPath).delete()
  }
}
