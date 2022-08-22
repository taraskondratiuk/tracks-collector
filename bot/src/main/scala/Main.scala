import bot.Bot.BotServiceGrpc
import clients.{PersistenceClient, SpotifyClient, YoutubeClient}
import io.grpc.ServerBuilder
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession

import scala.concurrent.ExecutionContext

object Main {

  def main(args: Array[String]): Unit = {
    val spotifyClient = new SpotifyClient(sys.env("SPOTIFY_CLIENT_ID"), sys.env("SPOTIFY_CLIENT_SECRET"))
    val youtubeClient = new YoutubeClient(sys.env("YOUTUBE_API_KEY"))
    val persistenceClient = new PersistenceClient(sys.env("PERSISTENCE_INFO_DIR"))
    val bot = new Bot(sys.env("TRACKS_COLLECTOR_BOT_TOKEN"), spotifyClient, youtubeClient, persistenceClient)
    val botsApi = new TelegramBotsApi(classOf[DefaultBotSession])
    botsApi.registerBot(bot)
    println("bot started")

    val botGrpcServer = ServerBuilder
      .forPort(sys.env("GRPC_PORT").toInt)
      .addService(BotServiceGrpc.bindService(new BotServiceImpl(bot.sendTrack), ExecutionContext.global))
      .build
      .start
  }
}
