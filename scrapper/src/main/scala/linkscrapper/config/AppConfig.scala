package linkscrapper.config

import cats.effect.IO
import pureconfig.{ConfigReader, ConfigSource}
import pureconfig.ConfigConvert.catchReadError
import pureconfig.configurable.genericMapReader

final case class AppConfig(
  scheduler: SchedulerConfig,
  server: ServerConfig,
  db: DbConfig,
  transport: TransportConfig
) derives ConfigReader

object AppConfig:
  def load: IO[AppConfig] =
    IO.delay(ConfigSource.default.loadOrThrow[AppConfig])
