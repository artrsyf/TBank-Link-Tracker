package linkscrapper.pkg.Client

import java.time.Instant

trait LinkClient[F[_]] {
  def getLastUpdate(url: String): F[Either[String, Instant]]
}