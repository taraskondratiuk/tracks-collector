import bot.Bot.{BotServiceGrpc, TrackRequest, TrackResponse}

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class BotServiceImpl(sendTrackMethod: String => Unit) extends BotServiceGrpc.BotService {
  override def sendTrack(request: TrackRequest): Future[TrackResponse] = {
    Try {
      sendTrackMethod(request.pathToTrack)
    } match {
      case Success(_) => Future.successful(TrackResponse())
      case Failure(e) => Future.successful(TrackResponse(e.getMessage))
    }
  }
}
