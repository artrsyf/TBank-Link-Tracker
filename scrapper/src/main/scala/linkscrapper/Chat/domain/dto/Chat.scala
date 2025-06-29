package linkscrapper.chat.domain.dto

import java.time.Instant

import sttp.tapir.Schema
import tethys.{JsonReader, JsonWriter}

import linkscrapper.chat.domain.entity
import linkscrapper.chat.domain.model

final case class ApiErrorResponse(
  error: String,
) derives Schema, JsonReader, JsonWriter

def ChatEntityToModel(chatEntity: entity.Chat, createdAt: Instant): model.Chat =
  model.Chat(
    chatId = chatEntity.chatId,
    createdAt = createdAt,
  )
