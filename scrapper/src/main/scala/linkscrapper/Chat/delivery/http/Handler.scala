package linkscrapper.Chat.delivery.http

import linkscrapper.Chat.usecase.ChatUsecase
import linkscrapper.Chat.domain.dto
import linkscrapper.Chat.domain.entity
import linkscrapper.pkg.Controller.Controller

import cats.effect.IO
import cats.data.EitherT

import sttp.tapir.server.ServerEndpoint
import sttp.tapir.*
import sttp.tapir.json.tethysjson.jsonBody
import sttp.model.StatusCode

object ChatEndpoints:
    val createChatEndpoint: Endpoint[
        Unit,
        Long,
        dto.ApiErrorResponse,
        Unit,
        Any,
    ] = 
        endpoint.post
            .summary("Зарегистрироваь чат")
            .in("tg-chat" / path[Long]("id"))
            .out(statusCode(StatusCode.Ok))
            .errorOut(jsonBody[dto.ApiErrorResponse].and(statusCode(StatusCode.BadRequest)))
    
    val deleteChatEndpoint: Endpoint[
        Unit,
        Long,
        dto.ApiErrorResponse,
        Unit,
        Any,
    ] = 
        endpoint.delete
            .summary("Удалить чат")
            .in("tg-chat" / path[Long]("id"))
            .out(statusCode(StatusCode.Ok))
            .errorOut(jsonBody[dto.ApiErrorResponse].and(statusCode(StatusCode.BadRequest)))


class ChatHandler(
    chatUsecase: ChatUsecase[IO],
) extends Controller[IO]:
    private val createChat: ServerEndpoint[Any, IO] = 
        ChatEndpoints.createChatEndpoint.serverLogic { chatId =>
            chatUsecase.create(entity.Chat(chatId))
                .map(_.map(_ => ()))
        }

    private val deleteChat: ServerEndpoint[Any, IO] = 
        ChatEndpoints.deleteChatEndpoint.serverLogic { chatId =>
            EitherT(chatUsecase.delete(chatId)).value
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
