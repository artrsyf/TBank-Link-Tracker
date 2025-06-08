package linktracker.config

import cats.effect.IO
import pureconfig.{ConfigReader, ConfigSource}
import pureconfig.ConfigConvert.catchReadError
import pureconfig.configurable.genericMapReader

final case class ServerConfig(
    port: Int,
) derives ConfigReader

object ServerConfig:
  def load: IO[ServerConfig] =
    IO.delay(ConfigSource.default.loadOrThrow[ServerConfig])
