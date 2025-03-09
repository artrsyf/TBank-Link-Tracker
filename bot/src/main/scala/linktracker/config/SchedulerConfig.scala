package linktracker.config

import cats.effect.IO
import pureconfig.{ConfigReader, ConfigSource}
import pureconfig.ConfigConvert.catchReadError
import pureconfig.configurable.genericMapReader

final case class TelegramConfig(
  botToken: String,
) derives ConfigReader

object TelegramConfig:
  def load: IO[TelegramConfig] =
    IO.delay(ConfigSource.default.loadOrThrow[TelegramConfig])