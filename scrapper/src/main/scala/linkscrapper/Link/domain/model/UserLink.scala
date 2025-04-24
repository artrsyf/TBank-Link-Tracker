package linkscrapper.link.domain.model

import java.time.Instant

import doobie.implicits.javatimedrivernative.*
import doobie.postgres.implicits.*
import doobie.Read

type Tags    = List[String]
type Filters = List[String]

final case class UserLink(
    chatId: Long,
    linkId: Long,
    tags: Tags,
    filters: Filters,
    createdAt: Instant,
)

object UserLink {
    given Read[UserLink] = Read[(Long, Long, String, String, Instant)].map {
    case (chatId, linkId, tagsStr, filtersStr, createdAt) =>
      val tags = if tagsStr.isBlank then List.empty else tagsStr.split(",").toList
      val filters = if filtersStr.isBlank then List.empty else filtersStr.split(",").toList
      UserLink(chatId, linkId, tags, filters, createdAt)
  }
}
