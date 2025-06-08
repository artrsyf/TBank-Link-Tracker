package linkscrapper.config

import cats.effect.IO
import pureconfig.{ConfigReader, ConfigSource}
import pureconfig.ConfigConvert.catchReadError
import pureconfig.configurable.genericMapReader

final case class DbConfig(
    inUse: String,
    driver: String,
    host: String,
    port: Int,
    db: String,
    user: String,
    password: String
) derives ConfigReader:
  lazy val url: String = s"jdbc:postgresql://$host:$port/$db"

object DbConfig:
  def load: IO[DbConfig] =
    IO.delay(ConfigSource.default.loadOrThrow[DbConfig])
