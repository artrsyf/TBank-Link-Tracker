package linkscrapper.Link.domain.entity

/*TODO Separate*/
type Tags = List[String]
type Filters = List[String]

type Links = List[Link]

final case class Link(
    id: Long,
    url: String,
    tags: Tags,
    filters: Filters,
)