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

import linktracker.link.domain.dto
import linktracker.link.presenter.LinkPresenter

class TelegramBotPresenter[F[_]: Async: Parallel](client: SttpBackend[F, Any])(using api: Api[F])
  extends LongPollBot[F](api) with LinkPresenter[F] {

  override def onMessage(msg: Message): F[Unit] = {
    val chatId = ChatIntId(msg.chat.id)
    msg.text match {
      case Some("/start") =>
        signup(msg.chat.id)

      case Some(trackText) if trackText.startsWith("/track") => 
        val url = trackText.stripPrefix("/track ").trim
        trackUrl(msg.chat.id, url)

      case Some(untrackText) if untrackText.startsWith("/untrack") => 
        val url = untrackText.stripPrefix("/untrack ").trim
        untrackUrl(msg.chat.id, url)

      case Some(listText) if listText.startsWith("/list") => 
        getLinkList(msg.chat.id)

      case Some("/help") =>
        helpReference(msg.chat.id)

      case Some(cmd) if cmd.startsWith("/") =>
        Methods.sendMessage(chatId, "unknown command").exec.void

      case _ =>
        Methods.sendMessage(chatId, "not a command").exec.void
    }
  }

  override def publishLinkUpdate(chatId: Long, linkUpdate: dto.LinkUpdate): F[Unit] = {
    val message = s"🔔 Обновление для ссылки: ${linkUpdate.url}\n${linkUpdate.description}"
    Methods.sendMessage(ChatIntId(chatId), message).exec.void
  }

  def signup(chatId: Long): F[Unit] = {
    val request = basicRequest
      .post(uri"http://localhost:8080/tg-chat/${chatId}")

    client.send(request).attempt.flatMap {
      case Right(response) =>
        response.code match {
          case StatusCode.Ok =>
            Methods.sendMessage(ChatIntId(chatId), "✅ Регистрация прошла успешно!").exec.void
          case _ =>
            Methods.sendMessage(ChatIntId(chatId), s"⚠ Ошибка регистрации: ${response.code}").exec.void
        }

      case Left(_: SttpClientException.ConnectException) =>
        Methods.sendMessage(ChatIntId(chatId), "❌ Сервер недоступен. Попробуйте позже.").exec.void

      case Left(error) =>
        Methods.sendMessage(ChatIntId(chatId), s"❌ Произошла ошибка: ${error.getMessage}").exec.void
    }
  }

  def untrackUrl(chatId: Long, url: String): F[Unit] = {
    val removeLinkRequest = dto.RemoveLinkRequest(
      link = url,
    )

    val request = basicRequest
      .delete(uri"http://localhost:8080/links")
      .header("Tg-Chat-Id", chatId.toString)
      .body(removeLinkRequest.asJson)
      .contentType("application/json")
    
    client.send(request).attempt.flatMap {
      case Right(response) if response.code == StatusCode.Ok =>
        Methods.sendMessage(ChatIntId(chatId), "✅ URL успешно удален из трекинга!").exec.void

      case Right(response) =>
        Methods.sendMessage(ChatIntId(chatId), s"⚠ Ошибка при удалении URL: ${response.code}").exec.void

      case Left(_: SttpClientException.ConnectException) =>
        Methods.sendMessage(ChatIntId(chatId), "❌ Сервер недоступен. Попробуйте позже.").exec.void

      case Left(error) =>
        Methods.sendMessage(ChatIntId(chatId), s"❌ Ошибка: ${error.getMessage}").exec.void
    }
  }

  def trackUrl(chatId: Long, url: String): F[Unit] = {
    val addLinkRequest = dto.AddLinkRequest(
      link = url,
      tags = List("string"),
      filters = List("string"),
    )

    val request = basicRequest
      .post(uri"http://localhost:8080/links")
      .header("Tg-Chat-Id", chatId.toString)
      .body(addLinkRequest.asJson)
      .contentType("application/json")
    
    client.send(request).attempt.flatMap {
      case Right(response) if response.code == StatusCode.Ok =>
        Methods.sendMessage(ChatIntId(chatId), "✅ URL успешно добавлен в трекинг!").exec.void

      case Right(response) =>
        Methods.sendMessage(ChatIntId(chatId), s"⚠ Ошибка при добавлении URL: ${response.code}").exec.void

      case Left(_: SttpClientException.ConnectException) =>
        Methods.sendMessage(ChatIntId(chatId), "❌ Сервер недоступен. Попробуйте позже.").exec.void

      case Left(error) =>
        Methods.sendMessage(ChatIntId(chatId), s"❌ Ошибка: ${error.getMessage}").exec.void
    }
  }

  def getLinkList(chatId: Long): F[Unit] = {
    val request = basicRequest
      .get(uri"http://localhost:8080/links")
      .header("Tg-Chat-Id", chatId.toString)

    client.send(request).flatMap { response =>
      response.body match {
        case Right(json) =>
          val links = json.jsonAs[List[dto.LinkResponse]] match
            case Right(linkList) => linkList
            case Left(error) => 
              Methods.sendMessage(ChatIntId(chatId), s"Ошибка при обработке данных: $error").exec.void
              List()

          links.parTraverse { link =>
            val message = s"ID: ${link.id}\nURL: ${link.url}\nTags: ${link.tags.mkString(", ")}\nFilters: ${link.filters.mkString(", ")}"
            Methods.sendMessage(ChatIntId(chatId), message).exec
          }.void

        case Left(error) =>
          Methods.sendMessage(ChatIntId(chatId), s"Ошибка при запросе: $error").exec.void
      }
    }
  }

  def helpReference(chatId: Long): F[Unit] = {
    Methods.sendMessage(
      ChatIntId(chatId), 
      "✅ Справка /*TODO*/"
    ).exec.void
  }
}