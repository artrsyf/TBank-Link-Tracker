package linktracker.config

import cats.effect.IO
import pureconfig.{ConfigReader, ConfigSource}
import pureconfig.ConfigConvert.catchReadError
import pureconfig.configurable.genericMapReader
import linkscrapper.config.TransportConfig

final case class AppConfig(
  telegram: TelegramConfig,
  server: ServerConfig,
  transport: TransportConfig
) derives ConfigReader

object AppConfig:
  def load: IO[AppConfig] =
    IO.delay(ConfigSource.default.loadOrThrow[AppConfig])
