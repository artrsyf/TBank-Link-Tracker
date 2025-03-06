package linkscrapper.Link.domain.model

type Tags = List[String]
type Filters = List[String]

type Links = List[Link]

final case class Link(
    id: Long,
    chatId: Long,
    url: String,
    tags: Tags,
    filters: Filters,
)