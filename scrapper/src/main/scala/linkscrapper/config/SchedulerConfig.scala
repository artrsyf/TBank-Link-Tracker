package linkscrapper.config

import cats.effect.IO
import pureconfig.{ConfigReader, ConfigSource}
import pureconfig.ConfigConvert.catchReadError
import pureconfig.configurable.genericMapReader

final case class SchedulerConfig(
  cronExpression: String,
  threadNumber: Int,
) derives ConfigReader

object SchedulerConfig:
  def load: IO[SchedulerConfig] =
    IO.delay(ConfigSource.default.loadOrThrow[SchedulerConfig])