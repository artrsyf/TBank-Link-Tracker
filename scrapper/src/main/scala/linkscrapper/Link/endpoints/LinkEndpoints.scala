package linkscrapper.link.endpoints

import sttp.tapir.*
import sttp.model.StatusCode
import sttp.tapir.Endpoint
import sttp.tapir.json.tethysjson.jsonBody

import linkscrapper.link.domain.dto

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
