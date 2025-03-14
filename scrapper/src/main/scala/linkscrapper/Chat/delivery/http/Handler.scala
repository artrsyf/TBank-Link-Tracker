package linkscrapper.Chat.delivery.http

import cats.data.EitherT
import cats.effect.IO
import cats.implicits.catsSyntaxEither

import sttp.tapir.server.ServerEndpoint

import linkscrapper.Chat.domain.dto
import linkscrapper.Chat.domain.entity
import linkscrapper.Chat.endpoints.ChatEndpoints
import linkscrapper.Chat.usecase.ChatUsecase
import linkscrapper.pkg.Controller.Controller

class ChatHandler(
    chatUsecase: ChatUsecase[IO],
) extends Controller[IO]:
  private val createChat: ServerEndpoint[Any, IO] =
    ChatEndpoints.createChatEndpoint.serverLogic { chatId =>
      chatUsecase.create(entity.Chat(chatId)).map {
        case Right(chat)     => Right(())
        case Left(chatError) => Left(dto.ApiErrorResponse(chatError.message))
      }
    }

  private val deleteChat: ServerEndpoint[Any, IO] =
    ChatEndpoints.deleteChatEndpoint.serverLogic { chatId =>
      chatUsecase.delete(chatId).map {
        _.leftMap(err => dto.ApiErrorResponse(err.message))
      }
    }

  override def endpoints: List[ServerEndpoint[Any, IO]] =
    List(
      createChat,
      deleteChat,
    )

object ChatHandler:
  def make(
      chatUsecase: ChatUsecase[IO],
  ): ChatHandler =
    ChatHandler(chatUsecase)
