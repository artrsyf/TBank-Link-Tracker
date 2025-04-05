package linkscrapper.chat.repository.InMemory

import java.time.Instant

import cats.effect.Ref
import cats.effect.IO

import linkscrapper.chat.domain.dto
import linkscrapper.chat.domain.entity
import linkscrapper.chat.domain.model
import linkscrapper.chat.repository

final class InMemoryChatRepository(
    data: Ref[IO, Map[Long, model.Chat]]
) extends repository.ChatRepository[IO]:
  override def create(chatEntity: entity.Chat): IO[model.Chat] =
    IO.println(s"Successfully created chat with id: ${chatEntity.chatId}")

    for
      chatModel <- IO.pure(
        dto.ChatEntityToModel(chatEntity, Instant.now())
      )
      _ <- data.update(_ + (chatModel.chatId -> chatModel))
    yield chatModel

  override def delete(chatId: Long): IO[Unit] =
    IO.println(s"Successfully deleted chat with id: $chatId")

    data.update(_.removed(chatId))

  override def getById(chatId: Long): IO[Option[model.Chat]] =
    data.get.map(_.get(chatId))
