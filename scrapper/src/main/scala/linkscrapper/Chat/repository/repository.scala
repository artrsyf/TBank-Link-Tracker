package linkscrapper.Chat.repository

import linkscrapper.Chat.domain.entity
import linkscrapper.Chat.domain.model

trait ChatRepository[F[_]]:
  def create(chatEntity: entity.Chat): F[model.Chat]
  def delete(chatId: Long): F[Unit]
  def getById(chatId: Long): F[Option[model.Chat]]
