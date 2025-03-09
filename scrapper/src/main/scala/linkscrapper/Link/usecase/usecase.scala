package linkscrapper.Link.usecase

import linkscrapper.Link.domain.dto
import linkscrapper.Link.domain.entity
import linkscrapper.Link.repository.LinkRepository

import cats.effect.IO
import javax.print.DocFlavor.CHAR_ARRAY
import linkscrapper.Link.domain.entity.Link

trait LinkUsecase[F[_]]:
    def addLink(createRequest: dto.AddLinkRequest, chatId: Long): F[entity.Link]
    def updateLink(updatedLinkEntity: Link): F[entity.Link]
    def removeLink(linkUrl: String, chatId: Long): IO[entity.Link]
    def getLinksByChatId(chatId: Long): F[entity.Links]
    def getLinks: F[entity.Links]

object LinkUsecase:
    final private class Impl(
        linkRepo: LinkRepository[IO],
    ) extends LinkUsecase[IO]:
        override def addLink(createRequest: dto.AddLinkRequest, chatId: Long): IO[entity.Link] = 
            for 
                linkEntity <- IO.pure(dto.LinkAddRequestToEntity(createRequest, chatId))
                _ <- linkRepo.create(linkEntity)
            yield linkEntity

        override def updateLink(updatedLinkEntity: Link): IO[entity.Link] = 
            for
                _ <- linkRepo.update(updatedLinkEntity)
            yield updatedLinkEntity

        override def removeLink(linkUrl: String, chatId: Long): IO[entity.Link] = 
            for 
                deletedLinkModel <- linkRepo.delete(linkUrl)
                deletedLinkEntity = dto.LinkModelToEntity(deletedLinkModel)
            yield deletedLinkEntity

        override def getLinksByChatId(chatId: Long): IO[entity.Links] = 
            for 
                modelLinks <- linkRepo.getLinksByChatId(chatId)
                entityLinks = modelLinks.map{ linkModel => dto.LinkModelToEntity(linkModel) }
            yield entityLinks

        override def getLinks: IO[entity.Links] = 
            for 
                modelLinks <- linkRepo.getLinks
                entityLinks = modelLinks.map{ linkModel => dto.LinkModelToEntity(linkModel) }
            yield entityLinks
        
    def make(repo: LinkRepository[IO]): LinkUsecase[IO] =
        Impl(repo)