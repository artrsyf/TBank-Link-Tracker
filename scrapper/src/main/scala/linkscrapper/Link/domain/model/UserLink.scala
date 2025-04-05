package linkscrapper.link.domain.model

import java.time.Instant

type Tags    = List[String]
type Filters = List[String]

final case class UserLink(
    chatId: Long,
    linkId: Long,
    tags: Tags,
    filters: Filters,
    createdAt: Instant,
)