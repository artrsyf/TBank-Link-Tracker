package linkscrapper.pkg.Publisher.HttpPublisher

import cats.effect.IO
import org.typelevel.log4cats.Logger
import sttp.client3._
import tethys._
import tethys.jackson._

import linkscrapper.pkg.Publisher.LinkPublisher
import linkscrapper.link.domain.dto.LinkUpdate

class HttpPublisher(
  url: String,
  backend: SttpBackend[IO, Any],
  logger: Logger[IO],
) extends LinkPublisher[IO]:
  override def publishLinks(linkUpdates: List[LinkUpdate]): IO[Unit] =
    val request = basicRequest
      .post(uri"${url}")
      .body(linkUpdates.asJson)
      .contentType("application/json")

    for
      _ <- logger.info(s"Sending updates to bot-service | linkCount=${linkUpdates.length}")

      response <- backend.send(request).attempt
      _ <- response match {
        case Right(resp) if resp.code.isSuccess =>
          logger.info("Successfully sent updates to bot-service")
        case Right(resp) =>
          logger.warn(
            s"Unexpected response code from bot-service | linkCount=${linkUpdates.length} " +
              s"| status=${resp.code} | Body=${resp.body}"
          )
        case Left(error) =>
          logger.error(
            s"Error sending updates to bot-service | linkCount=${linkUpdates.length} | error=${error.getMessage}"
          )
      }
    yield ()
