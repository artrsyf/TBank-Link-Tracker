package linkscrapper.Chat.repository.InMemory

import linkscrapper.Chat.repository
import linkscrapper.Chat.domain.model
import linkscrapper.Chat.domain.entity
import linkscrapper.Chat.domain.dto

import cats.effect.Ref
import cats.effect.IO

final class InMemoryChatRepository(
    data: Ref[IO, Map[Long, model.Chat]]
) extends repository.ChatRepository[IO]:
    override def create(chatEntity: entity.Chat): IO[model.Chat] = 
        IO.println(s"Successfully created chat with id: ${chatEntity.chatId}")

        for 
            chatModel <- IO.pure(
                dto.ChatEntityToModel(chatEntity)
            )
            _ <- data.update(_ + (chatModel.chatId -> chatModel))
        yield chatModel

    override def delete(chatId: Long): IO[Unit] = 
        IO.println(s"Successfully deleted chat with id: $chatId")

        data.update(_.removed(chatId))
