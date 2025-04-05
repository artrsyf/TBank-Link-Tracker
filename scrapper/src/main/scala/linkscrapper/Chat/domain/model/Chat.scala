package linkscrapper.chat.domain.model

import java.time.Instant

final case class Chat(
    chatId: Long,
    createdAt: Instant
)
