package linkscrapper.Chat.usecase

import cats.effect.IO
import scala.languageFeature.existentials
import telegramium.bots.high.messageentities.MessageEntityFormat.Bold

import linkscrapper.Chat.domain.dto
import linkscrapper.Chat.domain.entity
import linkscrapper.Chat.domain.entity.ChatError
import linkscrapper.Chat.repository.ChatRepository

trait ChatUsecase[F[_]]:
    def create(chatEntity: entity.Chat): F[Either[ChatError, entity.Chat]]
    def delete(chatId: Long): F[Either[ChatError, Unit]]
    def check(chatId: Long): F[Boolean]

object ChatUsecase:
    final private class Impl(
        chatRepo: ChatRepository[IO],
    ) extends ChatUsecase[IO]:
        override def create(chatEntity: entity.Chat): IO[Either[ChatError, entity.Chat]] = 
            for 
                existingChat <- chatRepo.getById(chatEntity.chatId)
                result <- existingChat match
                    case Some(_) => IO.pure(Left(ChatError.ErrAlreadyRegistered))
                    case _ => chatRepo.create(chatEntity).as(Right(chatEntity))
            yield result

        override def delete(chatId: Long): IO[Either[ChatError, Unit]] = 
            chatRepo.delete(chatId).attempt.map {
                case Left(_)  => Left(ChatError.ErrDeletionInvalidChat)
                case Right(_) => Right(())
            }

        override def check(chatId: Long): IO[Boolean] = 
            chatRepo.getById(chatId).map {
                    case Some(_) => true
                    case _ => false 
            }
        
    def make(repo: ChatRepository[IO]): ChatUsecase[IO] =
        Impl(repo)