package linkscrapper.Link.domain.dto

import java.time.Instant

import sttp.tapir.Schema
import tethys.{JsonReader, JsonWriter}

import linkscrapper.Link.domain.entity
import linkscrapper.Link.domain.model

type Tags = List[String]
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

def LinkEntityToUpdate(linkEntity: entity.Link): LinkUpdate = 
    LinkUpdate(
        url = linkEntity.url,
        description = "Got update!",
        tgChatIds = List(1, 2, 3),
    )

def LinkAddRequestToEntity(createRequest: AddLinkRequest, chatId: Long): entity.Link = 
    entity.Link(
        id = 1,
        url = createRequest.link,
        tags = createRequest.tags,
        filters = createRequest.filters,
        chatId = chatId,
        updatedAt = Instant.now(),
    )

def LinkEntityToModel(linkEntity: entity.Link, createdAt: Instant): model.Link = 
    model.Link(
        id = linkEntity.id,
        url = linkEntity.url,
        tags = linkEntity.tags,
        filters = linkEntity.filters,
        chatId = linkEntity.chatId,
        createdAt = createdAt,
        updatedAt = linkEntity.updatedAt,
    )

def LinkModelToEntity(linkModel: model.Link): entity.Link = 
    entity.Link(
        id = linkModel.id,
        url = linkModel.url,
        tags = linkModel.tags,
        filters = linkModel.filters,
        chatId = linkModel.chatId,
        updatedAt = linkModel.updatedAt,
    )
