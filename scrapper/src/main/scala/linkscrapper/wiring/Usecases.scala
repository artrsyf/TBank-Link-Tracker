package linkscrapper.wiring

import linkscrapper.Chat.usecase.ChatUsecase
import linkscrapper.Chat.repository.ChatRepository
import cats.effect.IO

final case class Usecases(
  chatUsecase: ChatUsecase[IO]
)

object Usecases:
  def make(repos: Repositories): Usecases =
    val chatUsecase  = ChatUsecase.make(repos.chatRepo)

    Usecases(chatUsecase)