package linkscrapper.Link.repository.InMemory

import linkscrapper.Link.domain.model
import linkscrapper.Link.domain.entity
import linkscrapper.Link.domain.dto
import linkscrapper.Link.repository

import cats.effect.Ref
import cats.effect.IO
import linkscrapper.Link.domain.model.Link
import linkscrapper.Link.domain.model.Links

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

    override def update(linkEntity: entity.Link): IO[model.Link] = 
        for 
            linkModel <- IO.pure(
                dto.LinkEntityToModel(linkEntity)
            )
            _ <- data.update(_.updated(linkModel.url, linkModel))
            _ <- IO.println(s"Successfully updated link with URL: ${linkEntity.url}")
        yield linkModel

    override def delete(linkUrl: String): IO[model.Link] = 
        for {
            maybeLink <- data.get.map(_.get(linkUrl))
            link <- IO.fromOption(maybeLink)(new Exception(s"Link not found: $linkUrl"))
            _ <- data.update(_ - linkUrl)
            _ <- IO.println(s"Successfully deleted link with url: $linkUrl")
        } yield link

    override def getLinksByChatId(chatId: Long): IO[model.Links] = 
        for
            links <- data.get
            selectedChatLinks = links.values.filter(_.chatId == chatId).toList
        yield selectedChatLinks
    
    override def getLinks: IO[Links] = 
        for
            links <- data.get
            linksList = links.values.toList
        yield linksList
