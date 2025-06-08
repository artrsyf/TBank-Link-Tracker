package linkscrapper.link.domain.dto

import java.time.Instant

import sttp.tapir.Schema
import tethys.{JsonReader, JsonWriter}

import linkscrapper.link.domain.entity
import linkscrapper.link.domain.model

type Tags    = List[String]
type Filters = List[String]

final case class ApiErrorResponse(
    error: String,
) derives Schema, JsonReader, JsonWriter

type ListLinksResponse = List[LinkResponse]

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

def LinkAddRequestToEntity(createRequest: AddLinkRequest, chatId: Long): entity.Link =
  entity.Link(
    id = 1,
    url = createRequest.link,
    tags = createRequest.tags,
    filters = createRequest.filters,
    chatId = chatId,
    updatedAt = Instant.now(),
  )

def LinkModelToEntity(linkModel: model.Link, userLinkModel: model.UserLink): entity.Link =
  entity.Link(
    id = linkModel.id,
    url = linkModel.url,
    tags = userLinkModel.tags,
    filters = userLinkModel.filters,
    chatId = userLinkModel.chatId,
    updatedAt = linkModel.updatedAt,
  )
