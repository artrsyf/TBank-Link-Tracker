package linktracker.link.repository

import linktracker.link.domain.dto
import linktracker.link.domain.entity.ResponseMessage

trait LinkRepository[F[_]]:
  def getLinks(chatId: Long): F[Either[ResponseMessage, List[dto.LinkResponse]]]
  def create(addLinkRequest: dto.AddLinkRequest, chatId: Long): F[ResponseMessage]
  def delete(removeLinkRequest: dto.RemoveLinkRequest, chatId: Long): F[ResponseMessage]
