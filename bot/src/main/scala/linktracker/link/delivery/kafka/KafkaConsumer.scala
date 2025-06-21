package linktracker.link.delivery.kafka

import cats.effect.{Async, IO}
import cats.syntax.all._
import fs2._
import fs2.kafka._
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.typelevel.log4cats.Logger
import tethys._
import tethys.jackson._
import tethys.derivation.semiauto._

import linktracker.link.domain.dto.LinkUpdate
import linktracker.link.usecase.LinkUsecase

class KafkaLinkUpdatesConsumer[F[_]: Async](
  consumer: KafkaConsumer[F, String, Either[Throwable, List[LinkUpdate]]],
  linkUsecase: LinkUsecase[F],
  topic: String,
  logger: Logger[F]
) {
  import linktracker.link.domain.dto.LinkUpdate.given

  def process: F[Unit] =
    for {
      _ <- logger.info(s"Subscribing to Kafka topic: $topic")
      _ <- consumer.subscribeTo(topic)
      _ <- consumer
        .stream
        .evalMap { committable =>
          val valueEither = committable.record.value
          val offset      = committable.offset

          valueEither match {
            case Right(updates) =>
              logger.info(s"Received ${updates.size} link updates from Kafka") *>
                linkUsecase.serveLinks(updates).attempt.flatMap {
                  case Right(_) =>
                    logger.info(s"Successfully processed ${updates.size} updates") *>
                      offset.commit
                  case Left(err) =>
                    logger.error(s"Failed to process updates: ${err.getMessage}")
                }

            case Left(err) =>
              logger.error(s"Failed to deserialize message from Kafka: ${err.getMessage}")
              offset.commit
          }
        }
        .compile
        .drain
    } yield ()
}
