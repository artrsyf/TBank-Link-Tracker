package linktracker.dialog.repository

import linktracker.dialog.domain.model.DialogState

trait DialogRepository[F[_]] {
  def get(chatId: Long): F[Option[DialogState]]
  def put(chatId: Long, state: DialogState): F[Unit]
  def delete(chatId: Long): F[Unit]
}