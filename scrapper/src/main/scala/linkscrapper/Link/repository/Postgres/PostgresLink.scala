package linkscrapper.link.repository.Postgres

import java.sql.Timestamp
import java.time.Instant

import cats.effect.{IO, Ref}
import cats.implicits._
import doobie.implicits._
import doobie.{ConnectionIO, Transactor}
import org.typelevel.log4cats.Logger
import fs2.Stream

import linkscrapper.link.domain.{dto, entity, model}
import linkscrapper.link.repository

final class PostgresLinkRepository(
    transactor: Transactor[IO],
    logger: Logger[IO],
) extends repository.LinkRepository[IO]:
  private def now = IO.realTimeInstant

  override def createUserLink(linkEntity: entity.Link): IO[entity.Link] =
    for
      now <- now
      result <- {
        val insertLink: ConnectionIO[Long] =
          sql"""
          INSERT INTO links (url, updated_at)
          VALUES (${linkEntity.url}, ${Timestamp.from(now)})
          ON CONFLICT (url) DO UPDATE SET updated_at = EXCLUDED.updated_at
          RETURNING id
        """.query[Long].unique

        val insertUserLink: Long => ConnectionIO[Unit] = linkId =>
          sql"""
          INSERT INTO user_links (chat_id, link_id, tags, filters, created_at)
          VALUES (${linkEntity.chatId}, $linkId, ${linkEntity.tags.mkString(",")}, ${linkEntity.filters.mkString(
              ","
            )}, ${Timestamp.from(now)})
          ON CONFLICT DO NOTHING
        """.update.run.void

        for {
          linkId <- insertLink
          _      <- insertUserLink(linkId)
        } yield {
          val linkModel     = model.Link(linkId, linkEntity.url, now)
          val userLinkModel = model.UserLink(linkEntity.chatId, linkId, linkEntity.tags, linkEntity.filters, now)
          dto.LinkModelToEntity(linkModel, userLinkModel)
        }
      }.transact(transactor)
        .flatTap(link => logger.info(s"Successfully created UserLink | chatId=${link.chatId} url=${link.url}"))
    yield result

  override def update(linkModel: model.Link): IO[Option[model.Link]] =
    for
      updatedAt <- now
      affected <- sql"""
        UPDATE links SET updated_at = ${Timestamp.from(updatedAt)}
        WHERE url = ${linkModel.url}
      """.update.run.transact(transactor)
      _ <-
        if affected > 0 then logger.info(s"Successfully updated link | url=${linkModel.url}")
        else logger.warn(s"Can't update link, link not found | url=${linkModel.url}")
    yield if affected > 0 then Some(linkModel.copy(updatedAt = updatedAt)) else None

  override def deleteUserLink(linkUrl: String, chatId: Long): IO[Option[entity.Link]] =
    for
      maybeLink <- sql"SELECT id, url, updated_at FROM links WHERE url = $linkUrl"
        .query[model.Link]
        .option
        .transact(transactor)

      result <- maybeLink match
        case Some(linkModel) =>
          for
            maybeUserLink <- sql"""
              DELETE FROM user_links
              WHERE chat_id = $chatId AND link_id = ${linkModel.id}
              RETURNING chat_id, link_id, tags, filters, created_at
            """.query[model.UserLink].option.transact(transactor)

            _ <- logger.info(s"Successfully deleted user link | url=$linkUrl")
          yield maybeUserLink.map(userLink => dto.LinkModelToEntity(linkModel, userLink))

        case None =>
          logger.info(s"Can't delete user link, link not found | url=$linkUrl") *> IO.pure(None)
    yield result

  override def getUserLinksByChatId(chatId: Long): IO[entity.Links] =
    sql"""
      SELECT l.id, l.url, l.updated_at,
             ul.chat_id, ul.link_id, ul.tags, ul.filters, ul.created_at
      FROM links l
      JOIN user_links ul ON l.id = ul.link_id
      WHERE ul.chat_id = $chatId
    """
      .query[(model.Link, model.UserLink)]
      .map(dto.LinkModelToEntity.tupled)
      .to[List]
      .transact(transactor)

  override def getLinks: IO[model.Links] =
    sql"SELECT id, url, updated_at FROM links"
      .query[model.Link]
      .to[List]
      .transact(transactor)

  override def streamAllLinks: Stream[IO, model.Link] =
    sql"SELECT id, url, updated_at FROM links"
      .query[model.Link]
      .stream
      .transact(transactor)

  override def getUserLinksByLinkUrl(linkUrl: String): IO[entity.Links] =
    sql"""
      SELECT l.id, l.url, l.updated_at,
             ul.chat_id, ul.link_id, ul.tags, ul.filters, ul.created_at
      FROM links l
      JOIN user_links ul ON l.id = ul.link_id
      WHERE l.url = $linkUrl
    """
      .query[(model.Link, model.UserLink)]
      .map(dto.LinkModelToEntity.tupled)
      .to[List]
      .transact(transactor)
