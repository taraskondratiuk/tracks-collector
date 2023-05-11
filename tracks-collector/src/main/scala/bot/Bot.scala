package bot

import clients.{PersistenceClient, UrlValidator}
import clients.PersistenceClient.{DbErrorResponse, SuccessfulResponse}
import models.{Playlist, Track, TrackFilesGroup}
import models.TrackFilesGroup.{TrackFilesInvalidGroup, TrackFilesValidGroup, TrackFilesValidSingleElement}
import org.telegram.telegrambots.extensions.bots.commandbot.TelegramLongPollingCommandBot
import org.telegram.telegrambots.extensions.bots.commandbot.commands.BotCommand
import org.telegram.telegrambots.meta.api.methods.send.{SendAudio, SendMediaGroup, SendMessage}
import org.telegram.telegrambots.meta.api.objects._
import org.telegram.telegrambots.meta.api.objects.media.{InputMedia, InputMediaAudio}
import org.telegram.telegrambots.meta.bots.AbsSender

import scala.jdk.CollectionConverters.SeqHasAsJava
import scala.util.Try

class Bot(token: String,
          spotifyClient: UrlValidator,
          youtubeClient: UrlValidator,
          persistenceClient: PersistenceClient,
         ) extends TelegramLongPollingCommandBot {
  override def getBotToken: String = token

  override def getBotUsername: String = "tracks collector"

  private case class Media(name: String, url: String) {
    def toStringMarkdown(): String = {
      s"[${name.replaceAll("[\\[\\]]", "")}]($url)"
    }
  }

  private object Media {
    def apply(playlist: Playlist): Media = {
      Media(playlist.name, playlist.playlistUrl)
    }

    def apply(track: Track): Media = {
      Media(track.name, track.trackUrl)
    }
  }

  register(new BotCommand("start", "start") {
    override def execute(absSender: AbsSender, user: User, chat: Chat, arguments: Array[String]): Unit = {
      sendTextMsg(
        chat.getId.toString,
        "Welcome! Bot to collect music from Spotify/Youtube playlists. /help to get list of commands",
        absSender,
      )
    }
  })

  register(new BotCommand("help", "help") {
    override def execute(absSender: AbsSender, user: User, chat: Chat, arguments: Array[String]): Unit = {
      val commands = Seq(
        "/add <Spotify/Youtube playlist url>",
        "/remove <playlist number to remove>",
        "/list",
        "/download <Spotify/Youtube playlist/track/album url>",
      )
      sendTextMsg(
        chat.getId.toString,
        commands.mkString("\n"),
        absSender,
      )
    }
  })

  register(new BotCommand("add", "add") {
    override def execute(absSender: AbsSender, user: User, chat: Chat, arguments: Array[String]): Unit = {
      arguments.toList match {
        case Nil      =>
          sendTextMsg(
            chat.getId.toString,
            "/add command requires Spotify/Youtube playlist url in format\n/add <Spotify/Youtube playlist url>",
            absSender,
          )
        case url :: _ =>
          val maybeValidatedPlaylist = spotifyClient.maybeExtractPlaylistFromUrl(url)
            .orElse(youtubeClient.maybeExtractPlaylistFromUrl(url))
          maybeValidatedPlaylist match {
            case Some(playlist) =>
              val msgStr = persistenceClient.addTrackedPlaylist(playlist, chat.getId.toString) match {
                case SuccessfulResponse(_) =>
                  s"Playlist ${Media(playlist).toStringMarkdown()} saved"
                case DbErrorResponse(_)    =>
                  "Server error"
              }
              sendTextMsg(chat.getId.toString, msgStr, absSender)
            case None           =>
              sendTextMsg(chat.getId.toString, "Invalid url", absSender)
          }
      }
    }
  })

  register(new BotCommand("download", "download") {
    override def execute(absSender: AbsSender, user: User, chat: Chat, arguments: Array[String]): Unit = {
      arguments.toList match {
        case Nil =>
          sendTextMsg(
            chat.getId.toString,
            "/download command requires Spotify/Youtube playlist/track/album url in format" +
              "\n/download <Spotify/Youtube playlist/track/album url>",
            absSender,
          )
        case url :: _ =>
          val maybeValidatedPlaylist = spotifyClient.maybeExtractPlaylistFromUrl(url)
            .orElse(youtubeClient.maybeExtractPlaylistFromUrl(url))
          val maybeValidatedTrack = spotifyClient.maybeExtractTrackFromUrl(url)
            .orElse(youtubeClient.maybeExtractTrackFromUrl(url))

          val msg = (maybeValidatedPlaylist, maybeValidatedTrack) match {
            case (Some(playlist), _) =>
              persistenceClient.addUntrackedPlaylist(playlist, chat.getId.toString) match {
                case SuccessfulResponse(_) =>
                  s"Added ${Media(playlist).toStringMarkdown()} to download queue"
                case DbErrorResponse(_)    =>
                  "Server error"
              }
            case (_, Some(track))    =>
              persistenceClient.addUntrackedTrack(track, chat.getId.toString) match {
                case SuccessfulResponse(_) =>
                  s"Added ${Media(track).toStringMarkdown()} to download queue"
                case DbErrorResponse(_)    =>
                  "Server error"
              }
            case _ =>
              "Invalid url"
          }

          sendTextMsg(chat.getId.toString, msg, absSender)
      }
    }
  })

  register(new BotCommand("remove", "remove") {
    override def execute(absSender: AbsSender, user: User, chat: Chat, arguments: Array[String]): Unit = {
      arguments.toList match {
        case Nil                                            =>
          sendTextMsg(chat.getId.toString, "/remove command requires playlist num", absSender)
        case maybeNum :: _ if Try(maybeNum.toInt).isFailure =>
          sendTextMsg(chat.getId.toString, s"Not a number: $maybeNum", absSender)
        case num :: _                                       =>
          val playlistNum = num.toInt
          val msg = persistenceClient.removeTrackedPlaylist(playlistNum, chat.getId.toString) match {
            case DbErrorResponse(_)                 =>
              "Sever error"
            case SuccessfulResponse(None)           =>
              s"No playlist with number: $playlistNum"
            case SuccessfulResponse(Some(playlist)) =>
              s"Playlist ${Media(playlist).toStringMarkdown()} removed"
          }
          sendTextMsg(chat.getId.toString, msg, absSender)
      }
    }
  })

  register(new BotCommand("list", "list") {
    override def execute(absSender: AbsSender, user: User, chat: Chat, arguments: Array[String]): Unit = {
      val msgStr = persistenceClient.listTrackedPlaylists(chat.getId.toString) match {
        case DbErrorResponse(_)                     =>
          "Server error"
        case SuccessfulResponse(seq) if seq.isEmpty =>
          "You don't have any saved playlists"
        case SuccessfulResponse(seq)                =>
          seq
            .sortBy(_.tsInserted)
            .zipWithIndex
            .map { case (playlist, idx) => s"${idx + 1}) ${Media(playlist).toStringMarkdown()}" }
            .mkString("\n")
      }
      sendTextMsg(chat.getId.toString, msgStr, absSender)
    }
  })

  override def processNonCommandUpdate(update: Update): Unit = ()

  def sendTracks(tracksGroup: TrackFilesGroup, msgText: String, msgUrl: String, chatId: String): Unit = {
    val m = Media(msgText, msgUrl)
    tracksGroup match {
      case TrackFilesValidGroup(tracks)        =>
        val sendTrack = new SendMediaGroup()
        sendTrack.setChatId(chatId)
        val medias = tracks.zipWithIndex.map { case (trackFile, idx) =>
          val media = new InputMediaAudio()
          media.setMedia(trackFile.file, trackFile.name)
          media.setTitle(trackFile.name)
          if (idx == tracks.length - 1) {
            media.setCaption(m.toStringMarkdown())
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
        sendTrack.setCaption(m.toStringMarkdown())
        sendTrack.setParseMode("MARKDOWN")
        this.execute(sendTrack)
      case TrackFilesInvalidGroup(tracks)      =>
        val msg = s"Sorry, files '$tracks' from ${m.toStringMarkdown()} are too large. " +
          s"Telegram bot message size limit is 50mb"
        sendTextMsg(chatId, msg, this)
    }
  }

  private def sendTextMsg(chatId: String, msgStr: String, absSender: AbsSender): Unit = {
    val msg = new SendMessage(chatId, msgStr)
    msg.setParseMode("MARKDOWN")
    absSender.execute[Message, SendMessage](msg)
  }
}
