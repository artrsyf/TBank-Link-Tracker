package linkscrapper.link.usecase

import cats.effect.IO
import org.typelevel.log4cats.Logger
import fs2.Stream

import linkscrapper.link.domain.dto
import linkscrapper.link.domain.model
import linkscrapper.link.domain.entity
import linkscrapper.link.domain.entity.LinkError
import linkscrapper.link.repository.LinkRepository
import linkscrapper.link.domain.model.Link

trait LinkUsecase[F[_]]:
  def addUserLink(createRequest: dto.AddLinkRequest, chatId: Long): F[Either[LinkError, entity.Link]]
  def updateLink(linkModel: model.Link): F[Either[LinkError, model.Link]]
  def removeUserLink(linkUrl: String, chatId: Long): F[Either[LinkError, entity.Link]]
  def getUserLinksByChatId(chatId: Long): F[entity.Links]
  def getLinks: F[model.Links]
  def streamAllLinks: Stream[F, model.Link]
  def getUserLinksByLinkUrl(linkUrl: String): F[entity.Links]

object LinkUsecase:
  final private class Impl(
    linkRepo: LinkRepository[IO],
    clients: List[String],
    logger: Logger[IO]
  ) extends LinkUsecase[IO]:
    override def addUserLink(createRequest: dto.AddLinkRequest, chatId: Long): IO[Either[LinkError, entity.Link]] =
      if clients.exists(client => createRequest.link.startsWith(client)) then
        for
          linkEntity <- IO.pure(dto.LinkAddRequestToEntity(createRequest, chatId))
          _          <- linkRepo.createUserLink(linkEntity)
        yield Right(linkEntity)
      else
        logger.warn(s"Can't find client for link | url=${createRequest.link} | clients=${clients}")
        IO.pure(Left(LinkError.ErrInvalidUrl))

    override def updateLink(linkModel: model.Link): IO[Either[LinkError, model.Link]] =
      for
        udpatedLinkModel <- linkRepo.update(linkModel)
        result <- udpatedLinkModel match {
          case Some(linkModel) =>
            IO.pure(Right(linkModel))
          case None =>
            IO.pure(Left(LinkError.ErrNoSuchLink))
        }
      yield result

    override def removeUserLink(linkUrl: String, chatId: Long): IO[Either[LinkError, entity.Link]] =
      for
        deletedLinkModel <- linkRepo.deleteUserLink(linkUrl, chatId)
        result <- deletedLinkModel match {
          case Some(linkEntity) =>
            IO.pure(Right(linkEntity))
          case _ =>
            IO.pure(Left(LinkError.ErrNoSuchLink))
        }
      yield result

    override def getUserLinksByChatId(chatId: Long): IO[entity.Links] =
      for
        entityLinks <- linkRepo.getUserLinksByChatId(chatId)
      yield entityLinks

    override def getLinks: IO[model.Links] =
      for
        modelLinks <- linkRepo.getLinks
      yield modelLinks

    override def streamAllLinks: Stream[IO, Link] =
      linkRepo.streamAllLinks

    override def getUserLinksByLinkUrl(linkUrl: String): IO[entity.Links] =
      for
        entityLinks <- linkRepo.getUserLinksByLinkUrl(linkUrl)
      yield entityLinks

  def make(repo: LinkRepository[IO], clients: List[String], logger: Logger[IO]): LinkUsecase[IO] =
    Impl(repo, clients, logger)
