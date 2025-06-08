package linkscrapper.chat.repository

import linkscrapper.chat.domain.entity
import linkscrapper.chat.domain.model

trait ChatRepository[F[_]]:
  def create(chatEntity: entity.Chat): F[model.Chat]
  def delete(chatId: Long): F[Unit]
  def getById(chatId: Long): F[Option[model.Chat]]
