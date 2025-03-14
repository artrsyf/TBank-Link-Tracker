package linkscrapper.wiring

import cats.effect.IO

import linkscrapper.Chat.repository.ChatRepository
import linkscrapper.Chat.usecase.ChatUsecase
import linkscrapper.Link.repository.LinkRepository
import linkscrapper.Link.usecase.LinkUsecase

final case class Usecases(
    chatUsecase: ChatUsecase[IO],
    linkUsecase: LinkUsecase[IO],
)

object Usecases:
  def make(repos: Repositories, clients: List[String]): Usecases =
    val chatUsecase = ChatUsecase.make(repos.chatRepo)
    val linkUsecase = LinkUsecase.make(repos.linkRepo, clients)

    Usecases(
      chatUsecase,
      linkUsecase,
    )
