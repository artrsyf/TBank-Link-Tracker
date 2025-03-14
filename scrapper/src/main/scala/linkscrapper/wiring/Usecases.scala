package linkscrapper.wiring

import linkscrapper.Chat.repository.ChatRepository
import linkscrapper.Link.repository.LinkRepository
import linkscrapper.Chat.usecase.ChatUsecase
import linkscrapper.Link.usecase.LinkUsecase

import cats.effect.IO

final case class Usecases(
  chatUsecase: ChatUsecase[IO],
  linkUsecase: LinkUsecase[IO],
)

object Usecases:
  def make(repos: Repositories, clients: List[String]): Usecases =
    val chatUsecase  = ChatUsecase.make(repos.chatRepo)
    val linkUsecase = LinkUsecase.make(repos.linkRepo, clients)

    Usecases(
      chatUsecase,
      linkUsecase,
    )