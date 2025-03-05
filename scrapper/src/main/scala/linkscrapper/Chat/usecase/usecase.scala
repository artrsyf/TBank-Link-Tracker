package linkscrapper.Chat.usecase

import linkscrapper.Chat.domain.dto
import linkscrapper.Chat.domain.entity

import linkscrapper.Chat.repository.ChatRepository
import linkscrapper.Chat.domain.entity.Chat
import cats.effect.IO

trait ChatUsecase[F[_]]:
    def create(chatEntity: entity.Chat): F[entity.Chat]
    def delete(chatId: Long): F[Unit]

object ChatUsecase:
    final private class Impl(
        chatRepo: ChatRepository[IO],
    ) extends ChatUsecase[IO]:
        override def create(chatEntity: entity.Chat): IO[Chat] = 
            for 
                _ <- chatRepo.create(chatEntity)
            yield chatEntity

        override def delete(chatId: Long): IO[Unit] = 
            chatRepo.delete(chatId)
        
    def make(repo: ChatRepository[IO]): ChatUsecase[IO] =
        Impl(repo)