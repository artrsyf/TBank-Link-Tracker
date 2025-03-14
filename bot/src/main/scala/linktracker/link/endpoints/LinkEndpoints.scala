package linktracker.link.endpoints

import sttp.tapir.*
import sttp.tapir.Endpoint
import sttp.tapir.json.tethysjson.jsonBody
import sttp.model.StatusCode

import linktracker.link.domain.dto

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