package linkscrapper.link.repository

import linkscrapper.link.domain.model
import linkscrapper.link.domain.entity

trait LinkRepository[F[_]]:
  def createUserLink(linkEntity: entity.Link): F[entity.Link]
  def update(linkModel: model.Link): F[Option[model.Link]]
  def deleteUserLink(linkUrl: String, chatId: Long): F[Option[entity.Link]]
  def getUserLinksByChatId(chatId: Long): F[entity.Links]
  def getLinks: F[model.Links]
  def getUserLinksByLinkUrl(linkUrl: String): F[entity.Links]
