package linktracker.link.presenter.TelegramBotPresenter

import cats.effect.kernel.Async
import cats.Parallel
import cats.syntax.functor._
import cats.syntax.all._

import sttp.client3._
import sttp.model.StatusCode

import telegramium.bots.high._
import telegramium.bots.high.implicits._
import telegramium.bots.{ChatIntId, Message}

import tethys._
import tethys.jackson._

import linktracker.config.TelegramConfig
import linktracker.link.domain.dto
import linktracker.link.domain.entity.{CommandDescription, ResponseMessage}
import linktracker.link.presenter.LinkPresenter

trait CommandHandler[F[_]] {
  def description: String
  def execute(chatId: Long, args: Option[String]): F[Unit]
}

object Commands {
  
  def all[F[_]: Async: Parallel](bot: TelegramBotPresenter[F])(using api: Api[F]): Map[String, CommandHandler[F]] = Map(
    "/start"   -> new CommandHandler[F] {
      val description = CommandDescription.startCommandDescription
      def execute(chatId: Long, args: Option[String]) = bot.signup(chatId)
    },

    "/track"   -> new CommandHandler[F] {
      val description = CommandDescription.trackCommandDescription
      def execute(chatId: Long, args: Option[String]) = args match {
        case Some(url) => bot.trackUrl(chatId, url)
        case None      => Methods.sendMessage(ChatIntId(chatId), ResponseMessage.MissingUrlArgMessage.message).exec.void
      }
    },

    "/untrack" -> new CommandHandler[F] {
      val description = CommandDescription.untrackCommandDescription
      def execute(chatId: Long, args: Option[String]) = args match {
        case Some(url) => bot.untrackUrl(chatId, url)
        case None      => Methods.sendMessage(ChatIntId(chatId), ResponseMessage.MissingUrlArgMessage.message).exec.void
      }
    },

    "/list"    -> new CommandHandler[F] {
      val description = CommandDescription.listCommandDescription
      def execute(chatId: Long, args: Option[String]) = bot.getLinkList(chatId)
    },

    "/help"    -> new CommandHandler[F] {
      val description = CommandDescription.helpCommandDescription
      def execute(chatId: Long, args: Option[String]) = {
        val helpText = all(bot)
          .map { case (cmd, handler) => s"$cmd - ${handler.description}" }
          .mkString("\n")
        
        bot.helpReference(chatId, helpText)
      }
    }
  )
}

class TelegramBotPresenter[F[_]: Async: Parallel](
  client: SttpBackend[F, Any],
  telegramConfig: TelegramConfig,
)(using api: Api[F]) extends LongPollBot[F](api) with LinkPresenter[F] {
  private def basicSecuredRequest(chatId: Long) = basicRequest
      .header("Tg-Chat-Id", chatId.toString)
  
  override def onMessage(msg: Message): F[Unit] = {
    val chatId = ChatIntId(msg.chat.id)
    msg.text match {
      case Some(text) if text.startsWith("/") =>
        val parts = text.split(" ", 2)
        val command = parts.head
        val args = parts.lift(1).map(_.trim)

        Commands.all(this).get(command) match {
          case Some(handler) => handler.execute(msg.chat.id, args)
          case None => Methods.sendMessage(chatId, ResponseMessage.UnknownCommandMessage.message).exec.void
        }

      case _ =>
        Methods.sendMessage(chatId, ResponseMessage.NotCommandMessage.message).exec.void
    }
  }

  override def publishLinkUpdate(chatId: Long, linkUpdate: dto.LinkUpdate): F[Unit] = {
    Methods.sendMessage(
      ChatIntId(chatId), 
      ResponseMessage.IncomingLinkUpdateMessage(linkUpdate).message,
    ).exec.void
  }

  def signup(chatId: Long): F[Unit] = {
    val request = basicRequest
      .post(uri"${telegramConfig.scrapperServiceUrl}/tg-chat/${chatId}")

    client.send(request).attempt.flatMap {
      case Right(response) =>
        response.code match {
          case StatusCode.Ok =>
            Methods.sendMessage(ChatIntId(chatId), ResponseMessage.SuccessfullRegisterMessage.message).exec.void
          case _ =>
            Methods.sendMessage(ChatIntId(chatId), ResponseMessage.FailedRegisterMessage.message).exec.void
        }

      case Left(_: SttpClientException.ConnectException) =>
        Methods.sendMessage(ChatIntId(chatId), ResponseMessage.ServerUnavailableMessage.message).exec.void

      case Left(error) =>
        Methods.sendMessage(ChatIntId(chatId), ResponseMessage.ErrorMessage(error.getMessage).message).exec.void
    }
  }

  def untrackUrl(chatId: Long, url: String): F[Unit] = {
    val removeLinkRequest = dto.RemoveLinkRequest(
      link = url,
    )

    val request = basicSecuredRequest(chatId)
      .delete(uri"${telegramConfig.scrapperServiceUrl}/links")
      .body(removeLinkRequest.asJson)
      .contentType("application/json")
    
    client.send(request).attempt.flatMap {
      case Right(response) if response.code == StatusCode.Ok =>
        Methods.sendMessage(ChatIntId(chatId), ResponseMessage.SuccessfullLinkDeletionMessage.message).exec.void

      case Right(response) =>
        Methods.sendMessage(ChatIntId(chatId), ResponseMessage.FailedLinkDeltionMessage.message).exec.void

      case Left(_: SttpClientException.ConnectException) =>
        Methods.sendMessage(ChatIntId(chatId), ResponseMessage.ServerUnavailableMessage.message).exec.void

      case Left(error) =>
        Methods.sendMessage(ChatIntId(chatId), ResponseMessage.ErrorMessage(error.getMessage).message).exec.void
    }
  }

  def trackUrl(chatId: Long, url: String): F[Unit] = {
    val addLinkRequest = dto.AddLinkRequest(
      link = url,
      tags = List("string"),
      filters = List("string"),
    )

    val request = basicSecuredRequest(chatId)
      .post(uri"${telegramConfig.scrapperServiceUrl}/links")
      .body(addLinkRequest.asJson)
      .contentType("application/json")
    
    client.send(request).attempt.flatMap {
      case Right(response) if response.code == StatusCode.Ok =>
        Methods.sendMessage(ChatIntId(chatId), ResponseMessage.SuccessfullLinkAdditionMessage.message).exec.void

      case Right(response) =>
        Methods.sendMessage(ChatIntId(chatId), ResponseMessage.FailedLinkAdditionMessage.message).exec.void

      case Left(_: SttpClientException.ConnectException) =>
        Methods.sendMessage(ChatIntId(chatId), ResponseMessage.ServerUnavailableMessage.message).exec.void

      case Left(error) =>
        Methods.sendMessage(ChatIntId(chatId), ResponseMessage.ErrorMessage(error.getMessage).message).exec.void
    }
  }

  def getLinkList(chatId: Long): F[Unit] = {
    val request = basicSecuredRequest(chatId)
      .get(uri"${telegramConfig.scrapperServiceUrl}/links")

    client.send(request).flatMap { response =>
      response.body match {
        case Right(json) =>
          val links = json.jsonAs[List[dto.LinkResponse]] match
            case Right(linkList) => linkList
            case Left(error) => 
              Methods.sendMessage(ChatIntId(chatId), ResponseMessage.FailedParseJsonMessage(error.toString()).message).exec.void
              List()
          if links.isEmpty then
            Methods.sendMessage(ChatIntId(chatId), ResponseMessage.EmptyLinkListMessage.message).exec.void
          else
            links.parTraverse { link =>
              Methods.sendMessage(ChatIntId(chatId), ResponseMessage.LinkRepresentationMessage(link).message).exec
            }.void

        case Left(error) =>
          Methods.sendMessage(ChatIntId(chatId), ResponseMessage.RequestErrorMessage(error).message).exec.void
      }
    }
  }

  def helpReference(chatId: Long, helpText: String): F[Unit] = {
    Methods.sendMessage(
      ChatIntId(chatId), 
      ResponseMessage.HelpReferenceMessage(helpText).message
    ).exec.void
  }
}