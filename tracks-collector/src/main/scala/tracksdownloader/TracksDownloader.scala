package tracksdownloader

import com.typesafe.scalalogging.Logger
import models.{TrackSource, SpotifySource, YoutubeSource}

import scala.util.{Failure, Success, Try}
import sys.process._

class TracksDownloader(spotifyClientId: String, spotifyClientSecret: String) {
  private val log = Logger(getClass.getSimpleName)

  def downloadTrack(uri: String, outputDir: String, source: TrackSource, maybeTrackName: Option[String]): Unit = {
    log.info(s"starting download $uri to $outputDir")
    val resTry = (source, maybeTrackName) match {
      case (YoutubeSource, Some(trackName)) =>
        tryDownloadYoutubeTrack(uri, trackName, outputDir)
      case (SpotifySource, _)               =>
        tryDownloadSpotifyTrack(uri, outputDir)
      case _                                =>
        Try(throw new Exception(s"invalid source ($source) and track name ($maybeTrackName)"))
    }
    resTry match {
      case Success(_) =>
        log.info(s"finished download $uri to $outputDir")
      case Failure(e) =>
        log.warn(s"failed to download $uri to $outputDir: ${e.getMessage}")
    }
  }

  private def processLogger(uri: String, outputDir: String, cmd: String): ProcessLogger = {
    new ProcessLogger {
      override def out(s: => String): Unit = {
        log.info(s"downloading $uri to $outputDir: $s")
      }

      override def err(s: => String): Unit = {
        if (s.contains("[INFO]") || s.contains("Completed") || s.replace("\n", "").trim.isBlank) {
          log.info(s"downloading $uri to $outputDir: $s")
        } else {
          log.warn(s"error on downloading $uri to $outputDir [$cmd]: $s")
        }
      }

      override def buffer[T](f: => T): T = {
        f
      }
    }
  }

  private def tryDownloadYoutubeTrack(uri: String, title: String, outputDir: String): Try[Unit] = {
    Try {
      val validTitle = title
        .replace("/", " ")
        .replace("\\", " ")
        .replace("\"", "'")
      val cmd = s"""yt-dlp -x -f 'ba' --audio-format mp3 --embed-thumbnail -o "$outputDir/$validTitle.%(ext)s" "$uri""""
      cmd.!!(processLogger(uri, outputDir, cmd))
    }
  }

  private def tryDownloadSpotifyTrack(uri: String, outputDir: String): Try[Unit] = {
    Try {
      val cmd = s"""savify "$uri" -o "$outputDir" -q 192k -f mp3"""
      cmd.!!(processLogger(uri, outputDir, cmd))
    }
  }
}
