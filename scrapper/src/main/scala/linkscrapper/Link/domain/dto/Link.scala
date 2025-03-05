package linkscrapper.Link.domain.dto

import linkscrapper.Link.domain.model
import linkscrapper.Link.domain.entity

/*TODO Separate*/
type Tags = List[String]
type Filters = List[String]

final case class CreateLinkRequest(
    Id: Long,
    Url: String,
    Tags: Tags,
    Filters: Filters,
)

def LinkCreateRequestToEntity(createRequest: CreateLinkRequest): entity.Link = 
    entity.Link(
        Id = createRequest.Id,
        Url = createRequest.Url,
        Tags = createRequest.Tags,
        Filters = createRequest.Filters,
    )

def LinkEntityToModel(linkEntity: entity.Link, chatId: Long): model.Link = 
    model.Link(
        Id = linkEntity.Id,
        ChatId = chatId,
        Url = linkEntity.Url,
        Tags = linkEntity.Tags,
        Filters = linkEntity.Filters,
    )

def LinkModelToEntity(linkModel: model.Link): entity.Link = 
    entity.Link(
        Id = linkModel.Id,
        Url = linkModel.Url,
        Tags = linkModel.Tags,
        Filters = linkModel.Filters,
    )
