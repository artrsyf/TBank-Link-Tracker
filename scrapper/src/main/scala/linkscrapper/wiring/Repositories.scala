package linkscrapper.wiring

import cats.effect.{IO, Ref, Resource}
import doobie.hikari.HikariTransactor
import org.typelevel.log4cats.Logger

import linkscrapper.chat.domain.model.Chat
import linkscrapper.link.domain.model.Link
import linkscrapper.chat.repository.ChatRepository
import linkscrapper.link.repository.LinkRepository
import linkscrapper.chat.repository.InMemory.InMemoryChatRepository
import linkscrapper.link.repository.InMemory.InMemoryLinkRepository
import linkscrapper.chat.repository.Postgres.PostgresChatRepository
import linkscrapper.link.repository.Postgres.PostgresLinkRepository
import linkscrapper.link.domain.model.UserLink

final case class Repositories(
    chatRepo: ChatRepository[IO],
    linkRepo: LinkRepository[IO],
)

object Repositories:
  def makeInMemory(using logger: Logger[IO]): IO[Repositories] =
    for
      chatData     <- Ref.of[IO, Map[Long, Chat]](Map.empty)
      linkData     <- Ref.of[IO, Map[String, Link]](Map.empty)
      userLinkData <- Ref.of[IO, Map[(Long, Long), UserLink]](Map.empty)

      chatRepo = InMemoryChatRepository(chatData, logger)
      linkRepo = InMemoryLinkRepository(linkData, userLinkData, logger)
    yield Repositories(
      chatRepo,
      linkRepo,
    )

  def makePostgres(hikariTransactor: HikariTransactor[IO])(using logger: Logger[IO]): IO[Repositories] =
    IO.delay {
      val chatRepo = PostgresChatRepository(hikariTransactor, logger)
      val linkRepo = PostgresLinkRepository(hikariTransactor, logger)
      Repositories(chatRepo, linkRepo)
    }

  // TODO: Update config & remove exceptions
  def make(dbType: String, hikariTransactor: Option[HikariTransactor[IO]])(using logger: Logger[IO]): IO[Repositories] =
    dbType match {
      case "postgres" =>
        hikariTransactor match {
          case Some(xa) => makePostgres(xa)(using logger)
          case None     => IO.raiseError(new IllegalArgumentException("HikariTransactor is required for Postgres"))
        }
      case "inmemory" => makeInMemory
      case _          => IO.raiseError(new IllegalArgumentException(s"Unsupported dbType: $dbType"))
    }
