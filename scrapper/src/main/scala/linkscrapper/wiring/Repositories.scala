package linkscrapper.wiring

import linkscrapper.Chat.domain.model
import linkscrapper.Chat.repository.ChatRepository
import linkscrapper.Chat.repository.InMemory.InMemoryChatRepository
import cats.effect.{IO, Ref}

final case class Repositories(
  chatRepo: ChatRepository[IO]
)

object Repositories:
  def make: IO[Repositories] =
    for 
        chatData <- Ref.of[IO, Map[Long, model.Chat]](Map.empty)
        chatRepo = InMemoryChatRepository(chatData)
    yield Repositories(chatRepo)