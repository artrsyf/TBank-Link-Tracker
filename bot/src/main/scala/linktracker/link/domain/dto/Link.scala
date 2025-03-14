package linktracker.link.domain.dto

import sttp.tapir.Schema
import tethys.{JsonReader, JsonWriter}

type Tags = List[String]
type Filters = List[String]

final case class ApiErrorResponse(
    error: String,
) derives Schema, JsonReader, JsonWriter

final case class LinkUpdate(
    url: String,
    description: String,
    tgChatIds: List[Long],
) derives Schema, JsonReader, JsonWriter

final case class AddLinkRequest(
    link: String,
    tags: Tags,
    filters: Filters,
) derives Schema, JsonReader, JsonWriter

final case class LinkResponse(
    id: Long,
    url: String,
    tags: Tags,
    filters: Filters,
) derives Schema, JsonReader, JsonWriter

final case class RemoveLinkRequest(
    link: String,
) derives Schema, JsonReader, JsonWriter