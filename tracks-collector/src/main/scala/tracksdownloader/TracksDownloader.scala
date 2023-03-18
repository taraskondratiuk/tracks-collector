package tracksdownloader

import com.typesafe.scalalogging.Logger
import models.{TrackSource, SpotifySource, YoutubeSource}

import scala.util.{Failure, Success, Try}
import sys.process._

class TracksDownloader {
  private val log = Logger(getClass.getSimpleName)

  def downloadTrack(uri: String, outputDir: String, source: TrackSource): Unit = {
    log.info(s"starting download $uri to $outputDir")
    val resTry = source match {
      case YoutubeSource =>
        tryDownloadYoutubeTrack(uri, outputDir)
      case SpotifySource =>
        ??? //todo
    }
    resTry match {
      case Success(_) =>
        log.info(s"finished download $uri to $outputDir")
      case Failure(_) =>
        log.warn(s"failed to download $uri to $outputDir")
    }
  }

  private def tryDownloadYoutubeTrack(uri: String, outputDir: String): Try[Unit] = {
    val processLogger = new ProcessLogger {
      override def out(s: => String): Unit = {
        log.info(s"downloading $uri to $outputDir: $s")
      }

      override def err(s: => String): Unit = {
        log.warn(s"error on downloading $uri to $outputDir: $s")
      }

      override def buffer[T](f: => T): T = {
        f
      }
    }
    Try(s"""yt-dlp -f 'bestaudio[ext=m4a]' --output "$outputDir/%(title)s.mp3" $uri""".!!(processLogger))
  }
}
