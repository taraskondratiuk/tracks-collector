package tracksdownloader

import com.typesafe.scalalogging.Logger
import models.{TrackSource, SpotifySource, YoutubeSource}

import scala.util.{Failure, Success, Try}
import sys.process._

class TracksDownloader(spotifyClientId: String, spotifyClientSecret: String) {
  private val log = Logger(getClass.getSimpleName)

  def downloadTrack(uri: String, outputDir: String, source: TrackSource): Unit = {
    log.info(s"starting download $uri to $outputDir")
    val resTry = source match {
      case YoutubeSource =>
        tryDownloadYoutubeTrack(uri, outputDir)
      case SpotifySource =>
        tryDownloadSpotifyTrack(uri, outputDir)
    }
    resTry match {
      case Success(_) =>
        log.info(s"finished download $uri to $outputDir")
      case Failure(e) =>
        log.warn(s"failed to download $uri to $outputDir ${e.getMessage}")
    }
  }

  private def processLogger(uri: String, outputDir: String): ProcessLogger = {
    new ProcessLogger {
      override def out(s: => String): Unit = {
        log.info(s"downloading $uri to $outputDir: $s")
      }

      override def err(s: => String): Unit = {
        if (s.contains("[INFO]") || s.contains("Completed") || s.replace("\n", "").trim.isBlank) {
          log.info(s"downloading $uri to $outputDir: $s")
        } else {
          log.warn(s"error on downloading $uri to $outputDir: $s")
        }
      }

      override def buffer[T](f: => T): T = {
        f
      }
    }
  }

  private def tryDownloadYoutubeTrack(uri: String, outputDir: String): Try[Unit] = {
    Try {
      s"""yt-dlp -f 'bestaudio[ext=m4a]' --output "$outputDir/%(title)s.mp3" $uri""".!!(processLogger(uri, outputDir))
    }
  }

  private def tryDownloadSpotifyTrack(uri: String, outputDir: String): Try[Unit] = {
    Try {
      s"""savify "$uri" -o "$outputDir" -q 192k -f mp3""".!!(processLogger(uri, outputDir))
    }
  }
}
