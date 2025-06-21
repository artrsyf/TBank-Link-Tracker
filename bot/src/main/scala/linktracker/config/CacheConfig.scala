package linktracker.config

import cats.effect.IO
import pureconfig.{ConfigReader, ConfigSource}
import pureconfig.ConfigConvert.catchReadError
import pureconfig.configurable.genericMapReader

final case class CacheConfig(
  redisHost: String,
  redisPort: Int,
  redisDb: Int
) derives ConfigReader:
  lazy val url: String = s"redis://$redisHost"

object CacheConfig:
  def load: IO[CacheConfig] =
    IO.delay(ConfigSource.default.loadOrThrow[CacheConfig])
