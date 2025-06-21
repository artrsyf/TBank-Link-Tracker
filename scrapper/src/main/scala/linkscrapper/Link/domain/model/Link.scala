package linkscrapper.link.domain.model

import java.time.Instant

import doobie.implicits.javatimedrivernative.*
import doobie.Read

type Links = List[Link]

final case class Link(
  id: Long,
  url: String,
  updatedAt: Instant,
)

object Link {
  given Read[Link] = Read[(Long, String, Instant)].map(Link(_, _, _))
}
