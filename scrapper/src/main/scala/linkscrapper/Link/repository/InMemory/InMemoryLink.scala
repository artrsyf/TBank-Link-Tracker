package linkscrapper.link.repository.InMemory

import java.time.Instant

import scala.util.Random

import cats.effect.IO
import cats.effect.Ref
import org.typelevel.log4cats.Logger

import linkscrapper.link.domain.dto
import linkscrapper.link.domain.entity
import linkscrapper.link.domain.model
import linkscrapper.link.repository

final class InMemoryLinkRepository(
    linkData: Ref[IO, Map[String, model.Link]],
    userLinkData: Ref[IO, Map[(Long, Long), model.UserLink]],
    logger: Logger[IO],
) extends repository.LinkRepository[IO]:
  private def generateLinkId(): IO[Long] =
    IO(Random.between(1L, 999999L))

  override def createUserLink(linkEntity: entity.Link): IO[entity.Link] =
    for
      links <- linkData.get
      linkCheck = links.get(linkEntity.url)
      linkModel <- linkCheck match
        case Some(link) =>
          IO.pure(link)
        case _ =>
          for
            newLinkId <- generateLinkId()
            newLink = model.Link(
              newLinkId,
              linkEntity.url,
              Instant.now(),
            )
            _ <- linkData.update(_ + (linkEntity.url -> newLink))
          yield newLink

      userLinkModel = model.UserLink(
        chatId = linkEntity.chatId,
        linkId = linkModel.id,
        tags = linkEntity.tags,
        filters = linkEntity.filters,
        createdAt = Instant.now()
      )

      _ <- userLinkData.update(_ + (
        (linkEntity.chatId, linkModel.id) -> userLinkModel
      ))

      linkEntity = dto.LinkModelToEntity(linkModel, userLinkModel)
      _ <- logger.info(
        s"Successfully created UserLink | chatId=${linkEntity.chatId} url=${linkEntity.url}"
      )
    yield linkEntity

  override def update(linkModel: model.Link): IO[Option[model.Link]] =
    for
      links <- linkData.get
      linkCheck = links.get(linkModel.url)
      result <- linkCheck match {
        case Some(linkModel) =>
          val updatedLinkModel = linkModel.copy(updatedAt = Instant.now())
          for
            _ <- linkData.update(_ + (linkModel.url -> updatedLinkModel))
            _ <- logger.info(s"Successfully updated link | url=${linkModel.url}")
          yield Some(updatedLinkModel)
        case None =>
          logger.warn(s"Can't update link, link not found | url=${linkModel.url}")
          IO.pure(None)
      }
    yield result

  override def deleteUserLink(linkUrl: String, chatId: Long): IO[Option[entity.Link]] =
    for
      links     <- linkData.get
      userLinks <- userLinkData.get
      linkCheck = links.get(linkUrl)
      result <- linkCheck match
        case Some(linkModel) =>
          for
            _ <- userLinkData.update(_ - ((chatId, linkModel.id)))
            userLinkEntity = userLinks.get((chatId, linkModel.id)) match
              case Some(userLinkModel) => Some(dto.LinkModelToEntity(linkModel, userLinkModel))
              case None                => None

            _ <- logger.info(s"Successfully deleted link | url=${linkUrl}")
          yield userLinkEntity
        case None =>
          logger.info(s"Can't delete user link, link not found | url=${linkUrl}")
          IO.pure(None)
    yield result

  override def getUserLinksByChatId(chatId: Long): IO[entity.Links] =
    for
      links      <- linkData.get
      usersLinks <- userLinkData.get
      userLinks = usersLinks.filter(_._1._1 == chatId)
      result = userLinks.values.flatMap { userLinkModel =>
        links.values.find(_.id == userLinkModel.linkId).map { linkModel =>
          dto.LinkModelToEntity(linkModel, userLinkModel)
        }
      }.toList
    yield result

  override def getLinks: IO[model.Links] =
    for
      links <- linkData.get
    yield links.values.toList

  override def getUserLinksByLinkUrl(linkUrl: String): IO[entity.Links] =
    for
      links     <- linkData.get
      userLinks <- userLinkData.get

      result = links.get(linkUrl) match
        case Some(linkModel) =>
          userLinks.values
            .filter(_.linkId == linkModel.id)
            .map(userLink => dto.LinkModelToEntity(linkModel, userLink))
            .toList
        case _ =>
          List.empty
    yield result
