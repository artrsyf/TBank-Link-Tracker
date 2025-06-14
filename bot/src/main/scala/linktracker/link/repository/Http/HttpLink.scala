package linktracker.link.repository.Http

import cats.effect.IO
import cats.syntax.all._
import org.typelevel.log4cats.Logger
import sttp.client3._
import sttp.model.StatusCode
import tethys._
import tethys.jackson._

import linktracker.link.repository
import linktracker.link.domain.dto
import linktracker.link.domain.entity.ResponseMessage

final class HttpLinkRepository(
  url: String,
  client: SttpBackend[IO, Any],
  logger: Logger[IO]
) extends repository.LinkRepository[IO]:
  private def basicSecuredRequest(chatId: Long) = basicRequest
    .header("Tg-Chat-Id", chatId.toString)

  override def getLinks(chatId: Long): IO[Either[ResponseMessage, List[dto.LinkResponse]]] =
    val request = basicSecuredRequest(chatId)
      .get(uri"${url}/links")

    client.send(request).flatMap { response =>
      response.body match {
        case Right(json) =>
          json.jsonAs[List[dto.LinkResponse]] match {
            case Right(linkList) =>
              if linkList.isEmpty then
                IO.pure(Left(ResponseMessage.EmptyLinkListMessage))
              else
                IO.pure(Right(linkList))
            case Left(error) =>
              logger.warn(s"Can't parse JSON from scrapper-service response | error=${error.getMessage()}") *>
                IO.pure(Left(ResponseMessage.FailedParseJsonMessage(error.getMessage())))
          }

        case Left(error) =>
          logger.error(s"Can't send list request to scrapper-service | error=${error}") *>
            IO.pure(Left(ResponseMessage.RequestErrorMessage(error)))
      }
    }

  override def create(addLinkRequest: dto.AddLinkRequest, chatId: Long): IO[ResponseMessage] =
    val request = basicSecuredRequest(chatId)
      .post(uri"${url}/links")
      .body(addLinkRequest.asJson)
      .contentType("application/json")

    client.send(request).attempt.flatMap {
      case Right(response) if response.code == StatusCode.Ok =>
        logger.info(s"Successful track request to scrapper")
        IO.pure(ResponseMessage.SuccessfullLinkAdditionMessage)

      case Right(response) =>
        logger.warn(s"Unexpected track response status code | code=${response.code}")
        IO.pure(ResponseMessage.FailedLinkAdditionMessage)

      case Left(ex: SttpClientException.ConnectException) =>
        logger.error(s"Can't send track request to scrapper-service | error=${ex}")
        IO.pure(ResponseMessage.ServerUnavailableMessage)

      case Left(error) =>
        logger.error(s"Can't send track request to scrapper-service | error=${error.getMessage}")
        IO.pure(ResponseMessage.ErrorMessage(error.getMessage))
    }

  override def delete(removeLinkRequest: dto.RemoveLinkRequest, chatId: Long): IO[ResponseMessage] =
    val request = basicSecuredRequest(chatId)
      .delete(uri"${url}/links")
      .body(removeLinkRequest.asJson)
      .contentType("application/json")

    client.send(request).attempt.flatMap {
      case Right(response) if response.code == StatusCode.Ok =>
        logger.info(s"Successful untrack request to scrapper")
        IO.pure(ResponseMessage.SuccessfullLinkDeletionMessage)

      case Right(response) =>
        logger.warn(s"Unexpected untrack response status code | code=${response.code}")
        IO.pure(ResponseMessage.FailedLinkDeltionMessage)

      case Left(ex: SttpClientException.ConnectException) =>
        logger.error(s"Can't send untrack request to scrapper-service | error=${ex}")
        IO.pure(ResponseMessage.ServerUnavailableMessage)

      case Left(error) =>
        logger.error(s"Can't send untrack request to scrapper-service | error=${error.getMessage}")
        IO.pure(ResponseMessage.ErrorMessage(error.getMessage))
    }
