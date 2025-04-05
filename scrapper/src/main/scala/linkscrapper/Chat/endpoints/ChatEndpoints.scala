package linkscrapper.chat.endpoints

import sttp.tapir.*
import sttp.tapir.json.tethysjson.jsonBody
import sttp.model.StatusCode

import linkscrapper.chat.domain.dto

object ChatEndpoints:
  val createChatEndpoint: Endpoint[
    Unit,
    Long,
    dto.ApiErrorResponse,
    Unit,
    Any,
  ] =
    endpoint.post
      .summary("Зарегистрироваь чат")
      .in("tg-chat" / path[Long]("id"))
      .out(statusCode(StatusCode.Ok))
      .errorOut(jsonBody[dto.ApiErrorResponse].and(statusCode(StatusCode.BadRequest)))

  val deleteChatEndpoint: Endpoint[
    Unit,
    Long,
    dto.ApiErrorResponse,
    Unit,
    Any,
  ] =
    endpoint.delete
      .summary("Удалить чат")
      .in("tg-chat" / path[Long]("id"))
      .out(statusCode(StatusCode.Ok))
      .errorOut(jsonBody[dto.ApiErrorResponse].and(statusCode(StatusCode.BadRequest)))
