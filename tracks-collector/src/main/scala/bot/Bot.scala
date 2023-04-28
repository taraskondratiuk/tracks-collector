package bot


import clients.{PersistenceClient, SpotifyClient, YoutubeClient}
import models.TrackFilesGroup
import models.TrackFilesGroup.{TrackFilesInvalidGroup, TrackFilesValidGroup, TrackFilesValidSingleElement}
import org.telegram.telegrambots.extensions.bots.commandbot.TelegramLongPollingCommandBot
import org.telegram.telegrambots.extensions.bots.commandbot.commands.BotCommand
import org.telegram.telegrambots.meta.api.methods.send.{SendAudio, SendMediaGroup, SendMessage}
import org.telegram.telegrambots.meta.api.objects._
import org.telegram.telegrambots.meta.api.objects.media.{InputMedia, InputMediaAudio}
import org.telegram.telegrambots.meta.bots.AbsSender

import java.io.File
import scala.jdk.CollectionConverters.SeqHasAsJava

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
          "/add <Spotify/Youtube playlist url>\n/remove <Spotify/Youtube playlist url>\n/list "
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
              val isSaved = persistenceClient.addPlaylist(playlist, chat.getId.toString)
              val msg = new SendMessage(
                chat.getId.toString,
                if (isSaved) {
                  s"Playlist ${formatTgMessage(playlist.name, playlist.playlistUrl)} saved"
                } else "Server error"
              )
              msg.setParseMode("MARKDOWN")
              absSender.execute[Message, SendMessage](msg)
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
              val isRemoved = persistenceClient.removePlaylist(playlist, chat.getId.toString)
              val msg = new SendMessage(
                chat.getId.toString,
                if (isRemoved) {
                  s"Playlist ${formatTgMessage(playlist.name, playlist.playlistUrl)} removed"
                } else "Server error"
              )
              msg.setParseMode("MARKDOWN")
              absSender.execute[Message, SendMessage](msg)
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
      val (isRetrieved, addedPlaylists) = persistenceClient.listPlaylists(chat.getId.toString)
      val msg = (isRetrieved, addedPlaylists) match {
        case (false, _)              =>
          "Server error"
        case (_, seq) if seq.isEmpty =>
          "You don't have any saved playlists"
        case (_, seq)                =>
          seq.zipWithIndex.map { case (playlist, idx) => s"${idx + 1}) ${playlist.playlistUrl}" }.mkString("\n")
      }
      absSender.execute[Message, SendMessage](
        new SendMessage(chat.getId.toString, msg)
      )
    }
  })

  override def processNonCommandUpdate(update: Update): Unit = ()

  def sendTracks(tracksGroup: TrackFilesGroup, msgText: String, msgUrl: String, chatId: String): Unit = {
    tracksGroup match {
      case TrackFilesValidGroup(tracks)        =>
        val sendTrack = new SendMediaGroup()
        sendTrack.setChatId(chatId)
        val medias = tracks.zipWithIndex.map { case (trackFile, idx) =>
          val media = new InputMediaAudio()
          media.setMedia(trackFile.file, trackFile.name)
          media.setTitle(trackFile.name)
          if (idx == tracks.length - 1) {
            media.setCaption(formatTgMessage(msgText, msgUrl))
            media.setParseMode("MARKDOWN")
          }
          media.asInstanceOf[InputMedia]
        }
        sendTrack.setMedias(medias.asJava)
        this.execute(sendTrack)
      case TrackFilesValidSingleElement(track) =>
        val sendTrack = new SendAudio()
        sendTrack.setAudio(new InputFile(track.file))
        sendTrack.setChatId(chatId)
        sendTrack.setTitle(track.file.getName)
        sendTrack.setCaption(formatTgMessage(msgText, msgUrl))
        sendTrack.setParseMode("MARKDOWN")
        this.execute(sendTrack)
      case TrackFilesInvalidGroup(tracks)      =>
        val msg = new SendMessage(
          chatId,
          s"Sorry, files '$tracks' from ${formatTgMessage(msgText, msgUrl)} are too large. " +
            s"Telegram bot message size limit is 50mb",
        )
        msg.setParseMode("MARKDOWN")
        this.execute[Message, SendMessage](msg)
    }
  }

  private def formatTgMessage(text: String, url: String): String = {
    s"[$text]($url)"
  }
}
