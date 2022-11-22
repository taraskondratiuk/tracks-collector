import clients.{PersistenceClient, SpotifyClient, YoutubeClient}
import org.telegram.telegrambots.extensions.bots.commandbot.TelegramLongPollingCommandBot
import org.telegram.telegrambots.extensions.bots.commandbot.commands.BotCommand
import org.telegram.telegrambots.meta.api.methods.send.{SendAudio, SendMessage}
import org.telegram.telegrambots.meta.api.objects.{Chat, InputFile, Message, Update, User}
import org.telegram.telegrambots.meta.bots.AbsSender

import java.io.File

class Bot(token: String,
          spotifyClient: SpotifyClient,
          youtubeClient: YoutubeClient,
          persistenceClient: PersistenceClient,
         ) extends TelegramLongPollingCommandBot {
  override def getBotToken: String = token

  override def getBotUsername: String = "tracks collector"

  register(new BotCommand("start", "start") {
    override def execute(absSender: AbsSender, user: User, chat: Chat, arguments: Array[String]): Unit = {
      absSender.execute[Message, SendMessage](
        new SendMessage(
          chat.getId.toString,
          "Welcome! Bot to collect music from Spotify/Youtube playlists. /help to get list of commands"
        )
      )
    }
  })

  register(new BotCommand("help", "help") {
    override def execute(absSender: AbsSender, user: User, chat: Chat, arguments: Array[String]): Unit = {
      absSender.execute[Message, SendMessage](
        new SendMessage(
          chat.getId.toString,
          "/add <Spotify/Youtube playlist url>\n/remove <spotify/youtube playlist url>\n/list"
        )
      )
    }
  })

  register(new BotCommand("add", "add") {
    override def execute(absSender: AbsSender, user: User, chat: Chat, arguments: Array[String]): Unit = {
      arguments.toList match {
        case Nil      =>
          absSender.execute[Message, SendMessage](
            new SendMessage(
              chat.getId.toString,
              "/add command requires Spotify/Youtube playlist url in format\n/add <Spotify/Youtube playlist url>"
            )
          )
        case url :: _ =>
          val maybeValidatedPlaylist = spotifyClient.maybeExtractPlaylistFromUrl(url).orElse(youtubeClient.maybeExtractPlaylistFromUrl(url))
          maybeValidatedPlaylist match {
            case Some(playlist) =>
              persistenceClient.addPlaylist(playlist, chat.getId.toString)
              absSender.execute[Message, SendMessage](
                new SendMessage(
                  chat.getId.toString,
                  "Playlist saved"
                )
              )
            case None           =>
              absSender.execute[Message, SendMessage](
                new SendMessage(
                  chat.getId.toString,
                  "Invalid url"
                )
              )
          }
      }
    }
  })

  register(new BotCommand("remove", "remove") {
    override def execute(absSender: AbsSender, user: User, chat: Chat, arguments: Array[String]): Unit = {
      arguments.toList match {
        case Nil      =>
          absSender.execute[Message, SendMessage](
            new SendMessage(
              chat.getId.toString,
              "/remove command requires Spotify/Youtube playlist url in format\n/add <Spotify/Youtube playlist url>"
            )
          )
        case url :: _ =>
          val maybeValidatedPlaylist = spotifyClient.maybeExtractPlaylistFromUrl(url).orElse(youtubeClient.maybeExtractPlaylistFromUrl(url))
          maybeValidatedPlaylist match {
            case Some(playlist) =>
              persistenceClient.removePlaylist(playlist, chat.getId.toString)
              absSender.execute[Message, SendMessage](
                new SendMessage(
                  chat.getId.toString,
                  "Playlist removed"
                )
              )
            case None           =>
              absSender.execute[Message, SendMessage](
                new SendMessage(
                  chat.getId.toString,
                  "Invalid url"
                )
              )
          }
      }
    }
  })

  register(new BotCommand("list", "list") {
    override def execute(absSender: AbsSender, user: User, chat: Chat, arguments: Array[String]): Unit = {
      val addedPlaylists = persistenceClient.listPlaylists(chat.getId.toString)
      if (addedPlaylists.nonEmpty) {
        absSender.execute[Message, SendMessage](
          new SendMessage(chat.getId.toString, addedPlaylists.map(_.playlistUrl).mkString("\n"))
        )
      } else {
        absSender.execute[Message, SendMessage](
          new SendMessage(chat.getId.toString, "You haven't any saved playlists")
        )
      }
    }
  })

  override def processNonCommandUpdate(update: Update): Unit = ()

  def sendTrack(trackPath: String, chatId: String): Unit = {
    val sendTrack = new SendAudio()
    sendTrack.setAudio(new InputFile(new File(trackPath)))
    sendTrack.setChatId(chatId)
    this.execute(sendTrack)
  }
}
