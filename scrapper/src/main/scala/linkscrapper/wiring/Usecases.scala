package linkscrapper.wiring

import cats.effect.IO

import linkscrapper.chat.repository.ChatRepository
import linkscrapper.chat.usecase.ChatUsecase
import linkscrapper.link.repository.LinkRepository
import linkscrapper.link.usecase.LinkUsecase
import org.typelevel.log4cats.Logger

final case class Usecases(
  chatUsecase: ChatUsecase[IO],
  linkUsecase: LinkUsecase[IO],
)

object Usecases:
  def make(repos: Repositories, clients: List[String])(using logger: Logger[IO]): Usecases =
    val chatUsecase = ChatUsecase.make(repos.chatRepo)
    val linkUsecase = LinkUsecase.make(repos.linkRepo, clients, logger)

    Usecases(
      chatUsecase,
      linkUsecase,
    )
