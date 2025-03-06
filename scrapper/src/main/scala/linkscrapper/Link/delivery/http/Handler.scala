package linkscrapper.Link.delivery.http

import linkscrapper.Link.usecase.LinkUsecase
import linkscrapper.Link.domain.dto
import linkscrapper.Link.domain.entity
import linkscrapper.pkg.Controller.Controller

import sttp.tapir.server.ServerEndpoint
import cats.effect.IO
import sttp.tapir.*
import sttp.tapir.json.tethysjson.jsonBody
import sttp.model.StatusCode

object LinkEndpoints:
    val getLinksEndpoint: Endpoint[
        Unit,
        Long,
        dto.ApiErrorResponse,
        dto.ListLinksResponse,
        Any
    ] =
        endpoint.get
            .summary("Получить все отслеживаемые ссылки")
            .in("links")
            .in(header[Long]("Tg-Chat-Id"))
            .out(jsonBody[dto.ListLinksResponse].and(statusCode(StatusCode.Ok)))
            .errorOut(jsonBody[dto.ApiErrorResponse].and(statusCode(StatusCode.BadRequest)))

    val addLinkEndpoint: Endpoint[
        Unit,
        (Long, dto.AddLinkRequest),
        dto.ApiErrorResponse,
        dto.LinkResponse,
        Any
    ] =
        endpoint.post
            .summary("Добавить отслеживание ссылки")
            .in("links")
            .in(header[Long]("Tg-Chat-Id"))
            .in(jsonBody[dto.AddLinkRequest])
            .out(jsonBody[dto.LinkResponse].and(statusCode(StatusCode.Ok)))
            .errorOut(jsonBody[dto.ApiErrorResponse].and(statusCode(StatusCode.BadRequest)))

    val removeLinkEndpoint: Endpoint[
        Unit,
        (Long, dto.RemoveLinkRequest),
        dto.ApiErrorResponse,
        dto.LinkResponse,
        Any
    ] =
        endpoint.delete
            .summary("Убрать отслеживание ссылки")
            .in("links")
            .in(header[Long]("Tg-Chat-Id"))
            .in(jsonBody[dto.RemoveLinkRequest])
            .out(jsonBody[dto.LinkResponse].and(statusCode(StatusCode.Ok)))
            .errorOut(jsonBody[dto.ApiErrorResponse].and(statusCode(StatusCode.BadRequest)))

class LinkHandler(
    linkUsecase: LinkUsecase[IO],
) extends Controller[IO]:
    private val getLinks: ServerEndpoint[Any, IO] = 
        LinkEndpoints.getLinksEndpoint.serverLogic { chatId =>
            linkUsecase.getLinksByChatId(chatId)
                .map(links => Right(links.map(link => dto.LinkResponse(link.id, link.url, link.tags, link.filters))))
                .handleError(e => Left(dto.ApiErrorResponse(s"Ошибка получения ссылок: ${e.getMessage}")))
        }

    private val addLink: ServerEndpoint[Any, IO] = 
        LinkEndpoints.addLinkEndpoint.serverLogic { case (chatId, addRequest) =>
            linkUsecase.addLink(addRequest, chatId)
                .map(link => Right(dto.LinkResponse(link.id, link.url, link.tags, link.filters)))
                .handleError(e => Left(dto.ApiErrorResponse(s"Ошибка добавления ссылки: ${e.getMessage}")))
        }

    private val removeLink: ServerEndpoint[Any, IO] = 
        LinkEndpoints.removeLinkEndpoint.serverLogic { case (chatId, removeRequest) =>
            linkUsecase.removeLink(removeRequest.link, chatId)
                .map(link => Right(dto.LinkResponse(link.id, link.url, link.tags, link.filters)))
                .handleError(e => Left(dto.ApiErrorResponse(s"Ошибка удаления ссылки: ${e.getMessage}")))
        }

    override def endpoints: List[ServerEndpoint[Any, IO]] =
        List(
            getLinks,
            addLink,
            removeLink
        )

object LinkHandler:
  def make(
    linkUsecase: LinkUsecase[IO],
  ): LinkHandler =
    LinkHandler(linkUsecase)