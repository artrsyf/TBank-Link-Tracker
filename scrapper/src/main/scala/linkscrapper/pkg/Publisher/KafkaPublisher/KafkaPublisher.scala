package linkscrapper.pkg.Publisher.KafkaPublisher

import cats.effect.IO
import cats.syntax.all._
import fs2.kafka._
import fs2.Stream
import org.typelevel.log4cats.Logger
import tethys._
import tethys.jackson._

import linkscrapper.pkg.Publisher.LinkPublisher
import linkscrapper.link.domain.dto.LinkUpdate

class KafkaLinkPublisher(
  topic: String,
  producer: KafkaProducer[IO, String, String],
  logger: Logger[IO]
) extends LinkPublisher[IO] {

  override def publishLinks(linkUpdates: List[LinkUpdate]): IO[Unit] = {
    if (linkUpdates.isEmpty) {
      logger.info("Notjing to update, skipping")
    } else {
      val json   = linkUpdates.asJson
      val record = ProducerRecord(topic, "", json)

      for {
        _ <- logger.info(s"Sending updates to kafka topic $topic | linkCount=${linkUpdates.length}")

        result <- producer.produceOne(record).flatten.attempt

        _ <- result match {
          case Right(responseChunk) =>
            Stream
              .chunk(responseChunk)
              .evalTap { case (_, metadata) =>
                logger.info(
                  s"Successfully published ${linkUpdates.length} updates to Kafka | " +
                    s"topic=${metadata.topic()} | partition=${metadata.partition()} | offset=${metadata.offset()}"
                )
              }
              .compile
              .drain

          case Left(error) =>
            logger.error(
              s"Failed to publish ${linkUpdates.length} updates to Kafka | " +
                s"error=${error.getMessage}"
            )
        }
      } yield ()
    }
  }
}
