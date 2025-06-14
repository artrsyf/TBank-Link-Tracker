package linktracker.link.domain.dto

import cats.effect.IO
import fs2.kafka._
import sttp.tapir.Schema
import tethys._
import tethys.jackson._

type Tags    = List[String]
type Filters = List[String]

final case class ApiErrorResponse(
  error: String,
) derives Schema, JsonReader, JsonWriter

final case class LinkUpdate(
  url: String,
  description: String,
  tgChatIds: List[Long],
) derives Schema, JsonReader, JsonWriter

object LinkUpdate {
  given valueDeserializer: Deserializer[IO, Either[Throwable, List[LinkUpdate]]] =
    Deserializer
      .string[IO]
      .map { json =>
        json.jsonAs[List[LinkUpdate]]
      }
}

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
