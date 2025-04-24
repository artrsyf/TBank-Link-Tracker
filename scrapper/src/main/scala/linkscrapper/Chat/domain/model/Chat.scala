package linkscrapper.chat.domain.model

import java.time.Instant

import doobie.implicits.javatimedrivernative.*
import doobie.Read

final case class Chat(
    chatId: Long,
    createdAt: Instant
)

object Chat {
    given Read[Chat] = Read[(Long, Instant)].map(Chat(_, _))
}
