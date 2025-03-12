package linktracker.link.delivery.http

import sttp.tapir.server.ServerEndpoint
import sttp.tapir.*
import sttp.tapir.json.tethysjson.jsonBody
import sttp.model.StatusCode

import linktracker.link.domain.dto
import linktracker.link.usecase.LinkUsecase

import cats.effect.IO

trait Controller[F[_]]:
  def endpoints: List[ServerEndpoint[Any, F]]

object LinkEndpoints:
  val updatesEndpoint: Endpoint[
    Unit,
    List[dto.LinkUpdate],
    dto.ApiErrorResponse,
    Unit,
    Any
  ] = endpoint.post
    .summary("Отправить обновление")
    .in("updates")
    .in(jsonBody[List[dto.LinkUpdate]])
    .out(statusCode(StatusCode.Ok))
    .errorOut(jsonBody[dto.ApiErrorResponse].and(statusCode(StatusCode.BadRequest)))

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
