package linkscrapper.Chat.domain.dto

import linkscrapper.Chat.domain.entity
import linkscrapper.Chat.domain.model

import tethys.{JsonReader, JsonWriter}
import sttp.tapir.Schema

final case class ApiErrorResponse(
    error: String,
) derives Schema, JsonReader, JsonWriter

def ChatEntityToModel(chatEntity: entity.Chat): model.Chat = 
    model.Chat(
        ChatId = chatEntity.ChatId,
    )