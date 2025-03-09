package linkscrapper.Link.repository

import linkscrapper.Link.domain.model
import linkscrapper.Link.domain.entity

trait LinkRepository[F[_]]:
    def create(linkEntity: entity.Link): F[model.Link]
    def update(linkEntity: entity.Link): F[model.Link]
    def delete(linkUrl: String): F[model.Link]
    def getLinksByChatId(chatId: Long): F[model.Links]
    def getLinks: F[model.Links]