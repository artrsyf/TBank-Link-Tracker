package linktracker.chat.repository.Http

import cats.effect.IO
import cats.syntax.all._
import org.typelevel.log4cats.Logger
import sttp.client3._
import sttp.model.StatusCode
import tethys._
import tethys.jackson._

import linktracker.chat.repository
import linktracker.link.domain.entity.ResponseMessage

final class HttpChatRepository(
    url: String,
    client: SttpBackend[IO, Any],
    logger: Logger[IO]
) extends repository.ChatRepository[IO]:
    override def create(chatId: Long): IO[ResponseMessage] = 
        val request = basicRequest
            .post(uri"${url}/tg-chat/${chatId}")

        client.send(request).attempt.flatMap {
        case Right(response) =>
            response.code match {
            case StatusCode.Ok =>
                logger.info(s"Successful signup request to scrapper")
                IO.pure(ResponseMessage.SuccessfullRegisterMessage)
            case _ =>
                logger.warn(s"Unexpected signup response status code | code=${response.code}")
                IO.pure(ResponseMessage.FailedRegisterMessage)
            }

        case Left(ex: SttpClientException.ConnectException) =>
            logger.error(s"Can't send signup request to scrapper-service | error=${ex}")
            IO.pure(ResponseMessage.ServerUnavailableMessage)

        case Left(error) =>
            logger.error(s"Can't send signup request to scrapper-service | error=${error.getMessage}")
            IO.pure(ResponseMessage.ErrorMessage(error.getMessage))
        }