package linkscrapper.link.domain.entity

import java.time.Instant

type Tags    = List[String]
type Filters = List[String]
type Links   = List[Link]

final case class Link(
  id: Long,
  url: String,
  tags: Tags,
  filters: Filters,
  chatId: Long,
  updatedAt: Instant,
)
