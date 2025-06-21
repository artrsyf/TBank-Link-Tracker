package linktracker.chat.repository

import fs2.Stream

import linktracker.link.domain.entity.ResponseMessage

trait ChatRepository[F[_]]:
  def create(chatId: Long): F[ResponseMessage]
