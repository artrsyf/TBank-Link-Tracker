package linkscrapper.chat.repository.Postgres

import java.time.Instant

import cats.effect.{IO, Ref}
import doobie.implicits._
import doobie.Transactor
import org.typelevel.log4cats.Logger

import linkscrapper.chat.repository
import linkscrapper.chat.domain.entity
import linkscrapper.chat.domain.model
import linkscrapper.chat.domain.dto.ChatEntityToModel
import linkscrapper.chat.domain.model.Chat

final class PostgresChatRepository(
    transactor: Transactor[IO],
    logger: Logger[IO],
) extends repository.ChatRepository[IO]:
  override def create(chatEntity: entity.Chat): IO[model.Chat] =
    val createdAt = Instant.now()
    val chatModel = ChatEntityToModel(chatEntity, createdAt)

    val insertQuery =
      sql"""
        INSERT INTO chats (chat_id, created_at)
        VALUES (${chatModel.chatId}, ${java.sql.Timestamp.from(createdAt)})
      """.update.run

    for
      _ <- insertQuery.transact(transactor)
      _ <- logger.info(s"Successfully created chat | chatId=${chatModel.chatId}")
    yield chatModel

  override def delete(chatId: Long): IO[Unit] =
    val deleteQuery =
      sql"""
        DELETE FROM chats
        WHERE chat_id = $chatId
      """.update.run

    for
      _ <- deleteQuery.transact(transactor)
      _ <- logger.info(s"Successfully deleted chat | chatId=$chatId")
    yield ()

  override def getById(chatId: Long): IO[Option[Chat]] =
    sql"""
      SELECT chat_id, created_at
      FROM chats
      WHERE chat_id = $chatId
    """
      .query[model.Chat]
      .option
      .transact(transactor)
