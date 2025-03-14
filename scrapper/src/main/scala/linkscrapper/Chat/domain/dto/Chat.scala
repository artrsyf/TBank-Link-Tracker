package linkscrapper.Chat.domain.dto

import linkscrapper.Chat.domain.entity
import linkscrapper.Chat.domain.model

import tethys.{JsonReader, JsonWriter}
import sttp.tapir.Schema
import java.time.Instant

final case class ApiErrorResponse(
    error: String,
) derives Schema, JsonReader, JsonWriter

def ChatEntityToModel(chatEntity: entity.Chat, createdAt: Instant): model.Chat = 
    model.Chat(
        chatId = chatEntity.chatId,
        createdAt = createdAt,
    )