import java.io.{BufferedWriter, File, FileWriter}
import java.time.format.DateTimeFormatter
import scala.io.Source

trait PersistenceClient {
  val PERSISTENCE_INFO_DIR: String

  def getAlreadySavedUrls: Seq[String] = {
    val dir = new File(PERSISTENCE_INFO_DIR)

    if (dir.exists() && dir.isDirectory) {
      dir.listFiles().filter(_.isFile).toSeq.flatMap { file =>
        val src = Source.fromFile(file)
        val urls = src.getLines().toSeq
        src.close()
        urls
      }
    } else Seq.empty
  }

  def addUrlsToSave(allUrls: Seq[String]): Unit = {
    val newUrls = allUrls diff getAlreadySavedUrls
    if (newUrls.nonEmpty) {
      val dtFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss")
      val file = new File(s"$PERSISTENCE_INFO_DIR/${java.time.LocalDateTime.now().format(dtFormatter)}.txt")
      val bw = new BufferedWriter(new FileWriter(file))
      bw.write(newUrls.mkString("\n"))
      bw.close()
    }
  }
}
