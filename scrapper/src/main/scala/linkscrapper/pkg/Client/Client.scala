package linkscrapper.pkg.Client

import java.time.Instant

import tethys._
import tethys.jackson._

case class LinkUpdate(
    url: String,
    updatedAt: Instant,
    description: String,
) derives JsonWriter

trait LinkClient[F[_]]:
  def getUpdates(url: String, since: Instant): F[Either[String, List[LinkUpdate]]]
