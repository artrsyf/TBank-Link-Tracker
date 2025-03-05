package linkscrapper.wiring

import linkscrapper.Chat.domain.model.Chat
import linkscrapper.Link.domain.model.Link
import linkscrapper.Chat.repository.ChatRepository
import linkscrapper.Link.repository.LinkRepository
import linkscrapper.Chat.repository.InMemory.InMemoryChatRepository
import linkscrapper.Link.repository.InMemory.InMemoryLinkRepository

import cats.effect.{IO, Ref}

final case class Repositories(
  chatRepo: ChatRepository[IO],
  linkRepo: LinkRepository[IO],
)

object Repositories:
  def make: IO[Repositories] =
    for 
        chatData <- Ref.of[IO, Map[Long, Chat]](Map.empty)
        linkData <- Ref.of[IO, Map[String, Link]](Map.empty)

        chatRepo = InMemoryChatRepository(chatData)
        linkRepo = InMemoryLinkRepository(linkData)
    yield Repositories(
      chatRepo,
      linkRepo,
    )