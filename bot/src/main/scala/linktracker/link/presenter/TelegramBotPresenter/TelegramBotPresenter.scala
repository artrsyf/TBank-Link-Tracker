package linktracker.link.presenter.TelegramBotPresenter

import cats.effect.kernel.Async
import cats.Parallel
import cats.syntax.functor._
import cats.syntax.all._

import sttp.client3._
import sttp.model.StatusCode

import telegramium.bots.CallbackQuery
import telegramium.bots.high._
import telegramium.bots.high.implicits._
import telegramium.bots.{ChatIntId, Message}
import telegramium.bots.high.keyboards._

import tethys._
import tethys.jackson._

import org.typelevel.log4cats.Logger

import linktracker.config.TelegramConfig
import linktracker.dialog.domain.model.*
import linktracker.dialog.repository.DialogRepository
import linktracker.link.domain.dto
import linktracker.link.domain.entity.{CallbackEvents, CommandDescription, ResponseMessage}
import linktracker.link.presenter.LinkPresenter
import linktracker.link.repository.LinkRepository
import linktracker.chat.repository.ChatRepository

trait CommandHandler[F[_]] {
  def description: String
  def execute(chatId: Long, args: Option[String]): F[Unit]
}

object Commands {
  def all[F[_]: Async: Parallel](bot: TelegramBotPresenter[F])(using api: Api[F]): Map[String, CommandHandler[F]] = Map(
    "/start" -> new CommandHandler[F] {
      val description                                 = CommandDescription.startCommandDescription
      def execute(chatId: Long, args: Option[String]) = bot.signup(chatId)
    },
    "/track" -> new CommandHandler[F] {
      val description = CommandDescription.trackCommandDescription
      def execute(chatId: Long, args: Option[String]) = args match {
        case Some(url) if url.nonEmpty =>
          val trackWithUrl = bot.trackUrlWithMeta(chatId, url)
          bot.changeDialogState(chatId, AwaitingTags(chatId, url)) *>
            Methods.sendMessage(
              ChatIntId(chatId),
              ResponseMessage.EnterTagsMessage.message,
              replyMarkup = Some(bot.cancelTagsKeyboard),
            ).exec.void
        case _ => Methods.sendMessage(ChatIntId(chatId), ResponseMessage.MissingUrlArgMessage.message).exec.void
      }
    },
    "/untrack" -> new CommandHandler[F] {
      val description = CommandDescription.untrackCommandDescription
      def execute(chatId: Long, args: Option[String]) = args match {
        case Some(url) => bot.untrackUrl(chatId, url)
        case None      => Methods.sendMessage(ChatIntId(chatId), ResponseMessage.MissingUrlArgMessage.message).exec.void
      }
    },
    "/list" -> new CommandHandler[F] {
      val description                                 = CommandDescription.listCommandDescription
      def execute(chatId: Long, args: Option[String]) = bot.getLinkList(chatId)
    },
    "/help" -> new CommandHandler[F] {
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
    linkRepo: LinkRepository[F],
    chatRepo: ChatRepository[F],
    dialogRepo: DialogRepository[F],
    telegramConfig: TelegramConfig,
)(using api: Api[F], logger: Logger[F]) extends LongPollBot[F](api) with LinkPresenter[F] {
  private val cancelTagsButton =
    InlineKeyboardButtons.callbackData(text = "Пропустить", callbackData = CallbackEvents.cancelTagsButtonPressed)

  val cancelTagsKeyboard =
    InlineKeyboardMarkups.singleButton(cancelTagsButton)

  private val cancelFiltersButton =
    InlineKeyboardButtons.callbackData(text = "Пропустить", callbackData = CallbackEvents.cancelFiltersButtonPressed)

  val cancelFiltersKeyboard =
    InlineKeyboardMarkups.singleButton(cancelFiltersButton)

  private def handleCommand(msg: Message): F[Unit] = {
    val chatId = ChatIntId(msg.chat.id)
    msg.text match {
      case Some(text) if text.startsWith("/") =>
        val parts   = text.split(" ", 2)
        val command = parts.head
        val args    = parts.lift(1).map(_.trim)

        Commands.all(this).get(command) match {
          case Some(handler) => handler.execute(msg.chat.id, args)
          case None          => Methods.sendMessage(chatId, ResponseMessage.UnknownCommandMessage.message).exec.void
        }

      case _ =>
        Methods.sendMessage(chatId, ResponseMessage.NotCommandMessage.message).exec.void
    }
  }

  private def handleDialogState(state: DialogState, text: String): F[Unit] = state match {
    case AwaitingTags(chatId, url) =>
      logger.debug(s"Handling dialog state | dialogState=${state}")
      val tags = text.split(" ").toList.filter(_.nonEmpty)
      dialogRepo.put(chatId, AwaitingFilters(chatId, url, tags)) *>
        Methods.sendMessage(
          ChatIntId(chatId),
          ResponseMessage.EnterFiltersMessage.message,
          replyMarkup = Some(cancelFiltersKeyboard),
        ).exec.void

    case AwaitingFilters(chatId, url, tags) =>
      logger.debug(s"Handling dialog state | dialogState=${state}")
      val filters = text.split(" ").toList.filter(_.nonEmpty)
      dialogRepo.delete(chatId) *>
        trackUrlWithMeta(chatId, url)(tags, filters)

    case _ =>
      logger.error(s"Handling unknown dialog state | dialogState=${state}")
      Async[F].pure(())
  }

  override def onMessage(msg: Message): F[Unit] = {
    val chatId = msg.chat.id
    val text   = msg.text.getOrElse("")

    dialogRepo.get(chatId).flatMap {
      case Some(state) => handleDialogState(state, text)
      case None        => handleCommand(msg)
    }
  }

  override def onCallbackQuery(callbackQuery: CallbackQuery): F[Unit] = {
    val chatId    = callbackQuery.from.id
    val answerAck = Methods.answerCallbackQuery(callbackQuery.id).exec.void

    callbackQuery.data match {
      case Some(CallbackEvents.cancelTagsButtonPressed) =>
        println(s"logger class: ${logger.getClass}")
        logger.info(s"User skipped tags chatId=$chatId")
        dialogRepo.get(chatId).flatMap {
          case Some(state @ AwaitingTags(_, _)) =>
            handleDialogState(state, "") *> answerAck
          case _ =>
            logger.warn(s"User $chatId tried to skip tags, but state is not AwaitingTags") *> answerAck
        }

      case Some(CallbackEvents.cancelFiltersButtonPressed) =>
        logger.info(s"User skipped filters chatId=$chatId")
        dialogRepo.get(chatId).flatMap {
          case Some(state @ AwaitingFilters(_, _, _)) =>
            handleDialogState(state, "") *> answerAck
          case _ =>
            logger.warn(s"User $chatId tried to skip filters, but state is not AwaitingFilters") *> answerAck
        }

      case Some(other) =>
        logger.warn(s"Unhandled callback data: $other") *> answerAck

      case None =>
        logger.warn("Callback query with no data received") *> answerAck
    }
  }

  override def publishLinkUpdate(chatId: Long, linkUpdate: dto.LinkUpdate): F[Unit] = {
    val linkUpdateMessage = ResponseMessage.IncomingLinkUpdateMessage(linkUpdate).message
    val safeMessage =
      if (linkUpdateMessage.length > 4096)
        linkUpdateMessage.take(4093) + "..."
      else
        linkUpdateMessage
    logger.info(s"Sending link update message | message=${linkUpdateMessage}")
    Methods.sendMessage(
      ChatIntId(chatId),
      safeMessage,
    ).exec.void
  }

  def changeDialogState(chatId: Long, state: DialogState): F[Unit] =
    dialogRepo.put(chatId, state)

  def signup(chatId: Long): F[Unit] = {
    for {
      response <- chatRepo.create(chatId)
      _        <- Methods.sendMessage(ChatIntId(chatId), response.message).exec.void
    } yield ()
  }

  def untrackUrl(chatId: Long, url: String): F[Unit] = {
    val removeLinkRequest = dto.RemoveLinkRequest(
      link = url,
    )

    for {
      response <- linkRepo.delete(removeLinkRequest, chatId)
      _        <- Methods.sendMessage(ChatIntId(chatId), response.message).exec.void
    } yield ()
  }

  def trackUrlWithMeta(chatId: Long, url: String)(tags: List[String], filters: List[String]): F[Unit] = {
    val addLinkRequest = dto.AddLinkRequest(
      link = url,
      tags = tags,
      filters = filters,
    )

    for {
      response <- linkRepo.create(addLinkRequest, chatId)
      _        <- Methods.sendMessage(ChatIntId(chatId), response.message).exec.void
    } yield ()
  }

  def getLinkList(chatId: Long): F[Unit] = {
    for {
      response <- linkRepo.getLinks(chatId)
      _ <- response match {
        case Left(responseMessage) =>
          Methods.sendMessage(ChatIntId(chatId), responseMessage.message).exec.void
        case Right(links) =>
          links.parTraverse { link =>
            Methods.sendMessage(ChatIntId(chatId), ResponseMessage.LinkRepresentationMessage(link).message).exec
          }.void
      }
    } yield ()
  }

  def helpReference(chatId: Long, helpText: String): F[Unit] = {
    Methods.sendMessage(
      ChatIntId(chatId),
      ResponseMessage.HelpReferenceMessage(helpText).message
    ).exec.void
  }
}
