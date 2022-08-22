import clients.{PersistenceClient, SpotifyClient, YoutubeClient}
import org.scalatest.flatspec.AnyFlatSpec
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession

import scala.jdk.CollectionConverters.CollectionHasAsScala

class BotClientSpec extends AnyFlatSpec {
  val spotifyClient = new SpotifyClient("clientId", "clientSecret")
  val youtubeClient = new YoutubeClient("apiKey")
  val persistenceClient = new PersistenceClient("/tmp/botTest")
  val bot = new Bot("5304272710:AAGjK3CPkD_PAzok9wERjaOF71oEVq6Sk0Q", spotifyClient, youtubeClient, persistenceClient) //sys.env("TRACKS_COLLECTOR_BOT_TOKEN")

  "bot client" should "init bot" in {
    val botsApi = new TelegramBotsApi(classOf[DefaultBotSession])
    botsApi.registerBot(bot)
    bot.sendTrack("/home/taras/Downloads/123.mp3")
    Thread.sleep(100000)
  }
}
