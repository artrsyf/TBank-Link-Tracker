package linktracker.config

import cats.effect.IO
import pureconfig.{ConfigReader, ConfigSource}
import pureconfig.ConfigConvert.catchReadError
import pureconfig.configurable.genericMapReader

final case class AppConfig(
    telegram: TelegramConfig,
    server: ServerConfig,
) derives ConfigReader

object AppConfig:
  def load: IO[AppConfig] =
    IO.delay(ConfigSource.default.loadOrThrow[AppConfig])
