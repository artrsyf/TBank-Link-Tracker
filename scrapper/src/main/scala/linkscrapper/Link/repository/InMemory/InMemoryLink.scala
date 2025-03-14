package linkscrapper.Link.repository.InMemory

import linkscrapper.Link.domain.model
import linkscrapper.Link.domain.entity
import linkscrapper.Link.domain.dto
import linkscrapper.Link.repository

import cats.effect.Ref
import cats.effect.IO

/*TODO url key is not valid*/
final class InMemoryLinkRepository(
    data: Ref[IO, Map[String, model.Link]]
) extends repository.LinkRepository[IO]:
    override def create(linkEntity: entity.Link): IO[model.Link] = 
        for 
            linkModel <- IO.pure(
                dto.LinkEntityToModel(linkEntity)
            )
            _ <- data.update(_ + (linkModel.url -> linkModel))
            _ <- IO.println(s"Successfully created link with URL: ${linkEntity.url}")
        yield linkModel

    override def update(linkEntity: entity.Link): IO[Option[model.Link]] = 
        for 
            maybeLink <- data.get.map(_.get(linkEntity.url))
            result <- maybeLink match {
                case Some(linkModel) =>
                    val updatedLinkModel = dto.LinkEntityToModel(linkEntity)
                    data.update(_.updated(linkEntity.url, updatedLinkModel))

                    IO.println(s"Successfully updated link with URL: ${linkEntity.url}")

                    IO.pure(Some(updatedLinkModel))
                case None =>
                    IO.println(s"Link with URL ${linkEntity.url} not found")
                    
                    IO.pure(None)
            }
        yield result

    override def delete(linkUrl: String): IO[Option[model.Link]] = 
        for {
            maybeLink <- data.get.map(_.get(linkUrl))
            _ <- maybeLink match {
                case Some(link) => 
                    data.update(_ - linkUrl)
                    IO.println(s"Successfully deleted link with url: $linkUrl")
                case None => 
                    IO.println(s"Link not found with url: $linkUrl")
            }
        } yield maybeLink

    override def getLinksByChatId(chatId: Long): IO[model.Links] = 
        for
            links <- data.get
            selectedChatLinks = links.values.filter(_.chatId == chatId).toList
        yield selectedChatLinks

    override def getLinks: IO[model.Links] = 
        for
            links <- data.get
            linksList = links.values.toList
        yield linksList