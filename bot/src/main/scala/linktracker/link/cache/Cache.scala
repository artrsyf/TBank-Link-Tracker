package linktracker.link.cache

import linktracker.link.domain.dto.LinkResponse

trait LinkCache[F[_]]:
  def get(chatId: Long): F[List[LinkResponse]]
  def set(links: List[LinkResponse], chatId: Long): F[Unit]
  def delete(chatId: Long): F[Unit]
