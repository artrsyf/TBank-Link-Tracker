package linkscrapper.link.domain.model

import java.time.Instant

type Links = List[Link]

final case class Link(
    id: Long,
    url: String,
    updatedAt: Instant,
)
