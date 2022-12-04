import clients.{PersistenceClient, SpotifyClient, YoutubeClient}
import io.circe.parser
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession

import java.io.{File, PrintWriter}
import java.util.concurrent.Executors
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Using

object Main extends cask.MainRoutes {

  val spotifyClient = new SpotifyClient(sys.env("SPOTIFY_CLIENT_ID"), sys.env("SPOTIFY_CLIENT_SECRET"))
  val youtubeClient = new YoutubeClient(sys.env("YOUTUBE_API_KEY"))
  val persistenceClient = new PersistenceClient(sys.env("PERSISTENCE_DIR"))
  val bot = new Bot(sys.env("TRACKS_COLLECTOR_BOT_TOKEN"), spotifyClient, youtubeClient, persistenceClient)
  val botsApi = new TelegramBotsApi(classOf[DefaultBotSession])
  botsApi.registerBot(bot)
  println("bot started")

  case class SendTrackRequest(trackPath: String, chatId: String)

  override def port: Int = 8080

  override def host: String = "0.0.0.0"

  @cask.post("/sendTrack")
  def sendTrack(req: cask.Request): Unit = {
    import io.circe.generic.auto._
    val res = for {
      json     <- parser.parse(req.text())
      reqObj   <- json.as[SendTrackRequest]
      lockFile = new File(s"${reqObj.trackPath}.lock")
      _        = Using(new PrintWriter(lockFile))(pw => pw.write(System.currentTimeMillis().toString))
      _        = sendTrackFuture(reqObj.trackPath, reqObj.chatId).onComplete(_ => lockFile.delete())
    } yield ()
    res.left.foreach(e => throw e)
  }

  val ec: ExecutionContext = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

  def sendTrackFuture(trackPath: String, chatId: String): Future[Unit] = {
    Future(bot.sendTrack(trackPath, chatId))(ec)
  }

  initialize()
}
