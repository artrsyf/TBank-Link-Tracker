package linkscrapper.Link.usecase

import linkscrapper.Link.domain.dto
import linkscrapper.Link.domain.entity
import linkscrapper.Link.repository.LinkRepository

import cats.effect.IO
import javax.print.DocFlavor.CHAR_ARRAY

trait LinkUsecase[F[_]]:
    def addLink(createRequest: dto.CreateLinkRequest, chatId: Long): F[entity.Link]
    def removeLink(linkUrl: String, chatId: Long): F[Unit]
    def getLinksByChatId(chatId: Long): F[entity.Links]

object LinkUsecase:
    final private class Impl(
        linkRepo: LinkRepository[IO],
    ) extends LinkUsecase[IO]:
        override def addLink(createRequest: dto.CreateLinkRequest, chatId: Long): IO[entity.Link] = 
            for 
                linkEntity <- IO.pure(dto.LinkCreateRequestToEntity(createRequest))
                _ <- linkRepo.create(linkEntity, chatId)
            yield linkEntity


        override def removeLink(linkUrl: String, chatId: Long): IO[Unit] = 
            linkRepo.delete(linkUrl)

        override def getLinksByChatId(chatId: Long): IO[entity.Links] = 
            for 
                modelLinks <- linkRepo.getLinksByChatId(chatId)
                entityLinks = modelLinks.map{ linkModel => dto.LinkModelToEntity(linkModel) }
            yield entityLinks
        
    def make(repo: LinkRepository[IO]): LinkUsecase[IO] =
        Impl(repo)