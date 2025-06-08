package linkscrapper.chat.repository.InMemory

import java.time.Instant

import cats.effect.Ref
import cats.effect.IO
import org.typelevel.log4cats.Logger

import linkscrapper.chat.domain.dto
import linkscrapper.chat.domain.entity
import linkscrapper.chat.domain.model
import linkscrapper.chat.repository

final class InMemoryChatRepository(
    data: Ref[IO, Map[Long, model.Chat]],
    logger: Logger[IO],
) extends repository.ChatRepository[IO]:
  override def create(chatEntity: entity.Chat): IO[model.Chat] =
    for
      chatModel <- IO.pure(
        dto.ChatEntityToModel(chatEntity, Instant.now())
      )
      _ <- data.update(_ + (chatModel.chatId -> chatModel))

      _ <- logger.info(s"Successfully created chat | chatId=${chatEntity.chatId}")
    yield chatModel

  override def delete(chatId: Long): IO[Unit] =
    for
      _ <- data.update(_.removed(chatId))
      _ <- logger.info(s"Successfully deleted chat | chatId=$chatId")
    yield ()

  override def getById(chatId: Long): IO[Option[model.Chat]] =
    data.get.map(_.get(chatId))
