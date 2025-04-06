package linkscrapper.wiring

import cats.effect.{IO, Ref}
import org.typelevel.log4cats.Logger

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
  def make(using logger: Logger[IO]): IO[Repositories] =
    for
      chatData <- Ref.of[IO, Map[Long, Chat]](Map.empty)
      linkData <- Ref.of[IO, Map[String, Link]](Map.empty)
      userLinkData <- Ref.of[IO, Map[(Long, Long), UserLink]](Map.empty)

      chatRepo = InMemoryChatRepository(chatData, logger)
      linkRepo = InMemoryLinkRepository(linkData, userLinkData, logger)
    yield Repositories(
      chatRepo,
      linkRepo,
    )
