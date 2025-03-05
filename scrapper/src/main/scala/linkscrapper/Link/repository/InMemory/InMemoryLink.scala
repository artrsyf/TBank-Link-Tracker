package linkscrapper.Link.repository.InMemory

import linkscrapper.Link.domain.model
import linkscrapper.Link.domain.entity
import linkscrapper.Link.domain.dto
import linkscrapper.Link.repository

import cats.effect.Ref
import cats.effect.IO
import linkscrapper.Link.domain.model.Link

final class InMemoryLinkRepository(
    data: Ref[IO, Map[String, model.Link]]
) extends repository.LinkRepository[IO]:
    override def create(linkEntity: entity.Link, chatId: Long): IO[model.Link] = 
        IO.println(s"Successfully created link with URL: ${linkEntity.Url}")

        for 
            linkModel <- IO.pure(
                dto.LinkEntityToModel(linkEntity, chatId)
            )
            _ <- data.update(_ + (linkModel.Url -> linkModel))
        yield linkModel

    override def delete(linkUrl: String): IO[Unit] = 
        IO.println(s"Successfully deleted link with url: $linkUrl")

        data.update(_.removed(linkUrl))

    override def getLinksByChatId(chatId: Long): IO[model.Links] = 
        for
            links <- data.get
            selectedChatLinks = links.values.filter(_.ChatId == chatId).toList
        yield selectedChatLinks
