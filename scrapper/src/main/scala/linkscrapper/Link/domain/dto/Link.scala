package linkscrapper.Link.domain.dto

import linkscrapper.Link.domain.model
import linkscrapper.Link.domain.entity

import tethys.{JsonReader, JsonWriter}
import sttp.tapir.Schema

/*TODO Separate*/
type Tags = List[String]
type Filters = List[String]

/*TODO Separate*/
final case class ApiErrorResponse(
    error: String,
) derives Schema, JsonReader, JsonWriter

type ListLinksResponse = List[LinkResponse]

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

def LinkAddRequestToEntity(createRequest: AddLinkRequest): entity.Link = 
    entity.Link(
        id = 1,
        url = createRequest.link,
        tags = createRequest.tags,
        filters = createRequest.filters,
    )

def LinkEntityToModel(linkEntity: entity.Link, chatId: Long): model.Link = 
    model.Link(
        id = linkEntity.id,
        chatId = chatId,
        url = linkEntity.url,
        tags = linkEntity.tags,
        filters = linkEntity.filters,
    )

def LinkModelToEntity(linkModel: model.Link): entity.Link = 
    entity.Link(
        id = linkModel.id,
        url = linkModel.url,
        tags = linkModel.tags,
        filters = linkModel.filters,
    )
