import cats.effect._
import cats.syntax.functor._
import cats.effect.kernel.Async
import cats.Parallel
import cats.syntax.all._

import sttp.client3._
import sttp.client3.impl.cats._
import sttp.client3.httpclient.cats.HttpClientCatsBackend
import sttp.tapir._
import sttp.tapir.json.tethysjson
import sttp.model.StatusCode

import tethys._
import tethys.jackson._
import tethys.derivation._

import telegramium.bots.high._
import telegramium.bots.high.implicits._
import telegramium.bots.{ChatIntId, Message}

import org.http4s.ember.client.EmberClientBuilder

import linktracker.config.AppConfig
import tethys.derivation.builder.WriterDescription.BuilderOperation.Add


type Tags = List[String]
type Filters = List[String]
final case class AddLinkRequest(
    link: String,
    tags: Tags,
    filters: Filters,
) derives Schema, JsonReader, JsonWriter

final case class LinkResponse(
    id: Long,
    url: String,
    tags: Tags,
    filters: Filters,
) derives Schema, JsonReader, JsonWriter

class MyLongPollBot[F[_]: Async: Parallel](client: SttpBackend[F, Any])(using api: Api[F]) 
  extends LongPollBot[F](api) {

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

  def trackUrl(chatId: Long, url: String): F[Unit] = {
    val addLinkRequest = AddLinkRequest(
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
          val links = json.jsonAs[List[LinkResponse]] match
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

  override def onMessage(msg: Message): F[Unit] = {
    val chatId = ChatIntId(msg.chat.id)
    msg.text match {
      case Some("/start") =>
        Methods.sendMessage(chatId, "start command").exec.void
        signup(msg.chat.id)

      case Some(trackText) if trackText.startsWith("/track") => 
        val url = trackText.stripPrefix("/track ").trim
        trackUrl(msg.chat.id, url)

      case Some(listText) if listText.startsWith("/list") => 
        getLinkList(msg.chat.id)

      case Some(cmd) if cmd.startsWith("/") =>
        Methods.sendMessage(chatId, "unknown command").exec.void

      case _ =>
        Methods.sendMessage(chatId, "not a command").exec.void
    }
  }
}

object Http4sBot extends IOApp.Simple {
  override def run: IO[Unit] =
    AppConfig.load.flatMap { appConfig =>
      EmberClientBuilder.default[IO].build.use { backend =>
        given Api[IO] = BotApi(backend, baseUrl = s"https://api.telegram.org/bot${appConfig.telegram.botToken}")
        HttpClientCatsBackend.resource[IO]().use { backend => 
          val bot = new MyLongPollBot[IO](backend)
          bot.start()
        }
      }
    }
}

