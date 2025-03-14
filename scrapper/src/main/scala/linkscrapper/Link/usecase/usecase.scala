package linkscrapper.Link.usecase

import linkscrapper.Link.domain.dto
import linkscrapper.Link.domain.entity
import linkscrapper.Link.repository.LinkRepository

import cats.effect.IO

trait LinkUsecase[F[_]]:
    def addLink(createRequest: dto.AddLinkRequest, chatId: Long): F[Either[dto.ApiErrorResponse, entity.Link]]
    def updateLink(updatedLinkEntity: entity.Link): F[Either[dto.ApiErrorResponse, entity.Link]]
    def removeLink(linkUrl: String, chatId: Long): F[Either[dto.ApiErrorResponse, entity.Link]]
    def getLinksByChatId(chatId: Long): F[entity.Links]
    def getLinks: F[entity.Links]

object LinkUsecase:
    final private class Impl(
        linkRepo: LinkRepository[IO],
        clients: List[String]
    ) extends LinkUsecase[IO]:
        override def addLink(createRequest: dto.AddLinkRequest, chatId: Long): IO[Either[dto.ApiErrorResponse, entity.Link]] = 
            if clients.exists(client => createRequest.link.startsWith(client)) then
                for 
                    linkEntity <- IO.pure(dto.LinkAddRequestToEntity(createRequest, chatId))
                    _ <- linkRepo.create(linkEntity)
                yield Right(linkEntity)
            else
                IO.pure(Left(dto.ApiErrorResponse("URL не соответствует поддерживаемым клиентам")))

        override def updateLink(updatedLinkEntity: entity.Link): IO[Either[dto.ApiErrorResponse, entity.Link]] =
            for 
                maybeUpdatedLink <- linkRepo.update(updatedLinkEntity)
                result <- maybeUpdatedLink match {
                    case Some(updatedLinkModel) =>
                        IO.pure(Right(dto.LinkModelToEntity(updatedLinkModel)))
                    case None =>
                        IO.pure(Left(dto.ApiErrorResponse(s"Ссылка с URL ${updatedLinkEntity.url} не найдена")))
                }
            yield result

        override def removeLink(linkUrl: String, chatId: Long): IO[Either[dto.ApiErrorResponse, entity.Link]] = 
            for 
                deletedLinkModel <- linkRepo.delete(linkUrl)
                result <- deletedLinkModel match {
                    case Some(link) => 
                        IO.pure(Right(dto.LinkModelToEntity(link)))
                    case _ => 
                        IO.pure(Left(dto.ApiErrorResponse(s"Ссылка с URL $linkUrl не найдена")))
                }
            yield result

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
        
    def make(repo: LinkRepository[IO], clients: List[String]): LinkUsecase[IO] =
        Impl(repo, clients)