package linkscrapper.config

import cats.effect.IO
import pureconfig.{ConfigReader, ConfigSource}

final case class TransportConfig(
  `type`: String,
  http: HttpConfig,
  kafka: KafkaConfig
) derives ConfigReader

object TransportConfig:
  def load: IO[TransportConfig] =
    IO.delay(ConfigSource.default.loadOrThrow[TransportConfig])

final case class HttpConfig(
  updatesHandlerEndpoint: String
) derives ConfigReader

object HttpConfig:
  def load: IO[HttpConfig] =
    IO.delay(ConfigSource.default.loadOrThrow[HttpConfig])

final case class KafkaConfig(
  topic: String,
  properties: Map[String, String]
) derives ConfigReader

object KafkaConfig:
  def load: IO[KafkaConfig] =
    IO.delay(ConfigSource.default.loadOrThrow[KafkaConfig])
