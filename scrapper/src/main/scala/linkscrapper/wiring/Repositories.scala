package linkscrapper.wiring

import cats.effect.{IO, Ref}

import linkscrapper.chat.domain.model.Chat
import linkscrapper.link.domain.model.Link
import linkscrapper.chat.repository.ChatRepository
import linkscrapper.link.repository.LinkRepository
import linkscrapper.chat.repository.InMemory.InMemoryChatRepository
import linkscrapper.link.repository.InMemory.InMemoryLinkRepository
import linkscrapper.link.domain.model.UserLink

final case class Repositories(
    chatRepo: ChatRepository[IO],
    linkRepo: LinkRepository[IO],
)

object Repositories:
  def make: IO[Repositories] =
    for
      chatData <- Ref.of[IO, Map[Long, Chat]](Map.empty)
      linkData <- Ref.of[IO, Map[String, Link]](Map.empty)
      userLinkData <- Ref.of[IO, Map[(Long, Long), UserLink]](Map.empty)

      chatRepo = InMemoryChatRepository(chatData)
      linkRepo = InMemoryLinkRepository(linkData, userLinkData)
    yield Repositories(
      chatRepo,
      linkRepo,
    )
