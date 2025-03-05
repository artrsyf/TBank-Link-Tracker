package linkscrapper.Link.domain.model

type Tags = List[String]
type Filters = List[String]

type Links = List[Link]

final case class Link(
    Id: Long,
    ChatId: Long,
    Url: String,
    Tags: Tags,
    Filters: Filters,
)