package linkscrapper.link.delivery.http

import cats.effect.IO
import cats.data.EitherT
import org.typelevel.log4cats.Logger
import sttp.tapir.server.ServerEndpoint

import linkscrapper.chat.usecase.ChatUsecase
import linkscrapper.link.domain.dto
import linkscrapper.link.endpoints.LinkEndpoints
import linkscrapper.link.usecase.LinkUsecase
import linkscrapper.pkg.Controller.Controller

object ChatMiddlewares:
  def chatValidation(checkChat: Long => IO[Boolean]): Long => IO[Either[dto.ApiErrorResponse, Long]] =
    chatId =>
      checkChat(chatId).map {
        case true  => Right(chatId)
        case false => Left(dto.ApiErrorResponse("Чат не найден"))
      }

class LinkHandler(
  chatUsecase: ChatUsecase[IO],
  linkUsecase: LinkUsecase[IO],
  logger: Logger[IO]
) extends Controller[IO]:
  private val checkChat = ChatMiddlewares.chatValidation(chatUsecase.check)

  private def withChatValidation[A, B](
    handlerFunc: Long => IO[Either[dto.ApiErrorResponse, B]]
  ): Long => IO[Either[dto.ApiErrorResponse, B]] =
    chatId =>
      checkChat(chatId).flatMap {
        case Right(validChatId) => handlerFunc(validChatId)
        case Left(error) =>
          logger.info(s"Unauthorized, can't find chat | chatId=${chatId}")
          IO.pure(Left(error))
      }

  private val getLinks: ServerEndpoint[Any, IO] =
    LinkEndpoints.getLinksEndpoint.serverLogic { chatId =>
      withChatValidation { _ =>
        linkUsecase.getUserLinksByChatId(chatId)
          .map(links => Right(links.map(link => dto.LinkResponse(link.id, link.url, link.tags, link.filters))))
      }(chatId)
    }

  private val addLink: ServerEndpoint[Any, IO] =
    LinkEndpoints.addLinkEndpoint.serverLogic { case (chatId, addRequest) =>
      withChatValidation { _ =>
        linkUsecase.addUserLink(addRequest, chatId)
          .flatMap {
            case Right(link) =>
              IO.pure(Right(dto.LinkResponse(link.id, link.url, link.tags, link.filters)))
            case Left(errorResp) =>
              IO.pure(Left(dto.ApiErrorResponse(errorResp.message)))
          }
      }(chatId)
    }

  private val removeLink: ServerEndpoint[Any, IO] =
    LinkEndpoints.removeLinkEndpoint.serverLogic { case (chatId, removeRequest) =>
      withChatValidation { _ =>
        linkUsecase.removeUserLink(removeRequest.link, chatId)
          .flatMap {
            case Right(link) =>
              IO.pure(Right(dto.LinkResponse(link.id, link.url, link.tags, link.filters)))
            case Left(errorResp) =>
              IO.pure(Left(dto.ApiErrorResponse(errorResp.message)))
          }
      }(chatId)
    }

  override def endpoints: List[ServerEndpoint[Any, IO]] =
    List(
      getLinks,
      addLink,
      removeLink
    )

object LinkHandler:
  def make(
    chatUsecase: ChatUsecase[IO],
    linkUsecase: LinkUsecase[IO],
    logger: Logger[IO],
  ): LinkHandler =
    LinkHandler(chatUsecase, linkUsecase, logger)
