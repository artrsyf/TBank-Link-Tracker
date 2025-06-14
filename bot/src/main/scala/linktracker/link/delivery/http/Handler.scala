package linktracker.link.delivery.http

import cats.effect.IO
import sttp.tapir.server.ServerEndpoint

import linktracker.link.domain.dto
import linktracker.link.endpoints.LinkEndpoints
import linktracker.link.usecase.LinkUsecase

trait Controller[F[_]]:
  def endpoints: List[ServerEndpoint[Any, F]]

class LinkHandler(
  linkUsecase: LinkUsecase[IO]
) extends Controller[IO]:
  private val updates: ServerEndpoint[Any, IO] =
    LinkEndpoints.updatesEndpoint.serverLogic { updatesRequest =>
      sendUpdates(updatesRequest).as(Right(()))
    }

  private def sendUpdates(updatesRequest: List[dto.LinkUpdate]): IO[Unit] =
    linkUsecase.serveLinks(updatesRequest)

  override def endpoints: List[ServerEndpoint[Any, IO]] =
    List(updates)
