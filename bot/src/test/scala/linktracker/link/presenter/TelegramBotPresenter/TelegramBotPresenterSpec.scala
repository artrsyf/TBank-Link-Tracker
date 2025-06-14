package linktracker.link.presenter.TelegramBotPresenter

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.effect.unsafe.IORuntime
import cats.effect.kernel.Async
import cats.Parallel

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.mockito.Mockito._
import org.mockito.ArgumentMatchers.{any, argThat, eq => eqTo}
import org.scalatestplus.mockito.MockitoSugar
import org.typelevel.log4cats.slf4j.Slf4jLogger
import sttp.client3._
import sttp.model.StatusCode
import telegramium.bots.{Chat, ChatIntId, Message}
import telegramium.bots.high._
import telegramium.bots.high.implicits._

import io.circe.syntax._
import io.circe.generic.auto._
import tethys.*
import tethys.jackson.*

import linktracker.config.TelegramConfig
import linktracker.dialog.repository.DialogRepository
import linktracker.link.domain.dto.{AddLinkRequest, LinkResponse}
import linktracker.dialog.domain.model.*
import linktracker.link.domain.entity.*
import linktracker.link.domain.dto.*
import linktracker.link.repository.Http.HttpLinkRepository
import linktracker.chat.repository.Http.HttpChatRepository
import linktracker.link.cache.LinkCache

class TelegramBotPresenterSpec extends AnyFunSuite with Matchers with MockitoSugar {
  given IORuntime = cats.effect.unsafe.IORuntime.global

  private val testChatId     = 123456789L
  private val testUrl        = "https://example.com"
  private val mockApi        = mock[Api[IO]]
  private val mockDialogRepo = mock[DialogRepository[IO]]
  private val mockBackend    = mock[SttpBackend[IO, Any]]
  private val mockCache      = mock[LinkCache[IO]]
  private val config         = TelegramConfig("fake")
  private val logger         = Slf4jLogger.getLogger[IO]
  private val linkRepository = new HttpLinkRepository("http://localhost:8080", mockBackend, mockCache, logger)
  private val chatRepository = new HttpChatRepository("http://localhost:8080", mockBackend, logger)

  when(mockCache.get(any())).thenReturn(IO.pure(List.empty))
  when(mockCache.set(any(), any())).thenReturn(IO.unit)
  when(mockCache.delete(any())).thenReturn(IO.unit)

  private def createPresenter() = new TelegramBotPresenter[IO](
    linkRepository,
    chatRepository,
    mockDialogRepo,
    config
  )(using Async[IO], Parallel[IO], mockApi, logger)

  test("should handle valid /track command with URL") {
    val presenter = createPresenter()
    val msg = Message(
      messageId = 1,
      date = 0,
      chat = Chat(id = testChatId, "private"),
      text = Some(s"/track $testUrl")
    )

    when(mockDialogRepo.get(testChatId)).thenReturn(IO.pure(None))
    when(mockDialogRepo.put(testChatId, AwaitingTags(testChatId, testUrl)))
      .thenReturn(IO.unit)
    when(mockApi.execute(Methods.sendMessage(
      ChatIntId(testChatId),
      ResponseMessage.EnterTagsMessage.message,
      replyMarkup = Some(presenter.cancelTagsKeyboard)
    ))).thenReturn(IO.unit)

    presenter.onMessage(msg).unsafeRunSync()

    verify(mockDialogRepo).put(testChatId, AwaitingTags(testChatId, testUrl))
    verify(mockApi).execute(Methods.sendMessage(
      ChatIntId(testChatId),
      ResponseMessage.EnterTagsMessage.message,
      replyMarkup = Some(presenter.cancelTagsKeyboard)
    ))
  }

  test("handle /untrack command successfully") {
    val presenter = createPresenter()
    val msg = Message(
      messageId = 1,
      date = 0,
      chat = Chat(id = testChatId, "private"),
      text = Some(s"/untrack $testUrl")
    )

    when(mockDialogRepo.get(testChatId)).thenReturn(IO.pure(None))

    val successfulResponse = Response("", StatusCode.Ok)
    when(mockBackend.send(any())).thenReturn(IO.pure(successfulResponse))

    when(mockApi.execute(any())).thenReturn(IO.unit)

    presenter.onMessage(msg).unsafeRunSync()

    verify(mockApi).execute(
      Methods.sendMessage(
        ChatIntId(testChatId),
        ResponseMessage.SuccessfullLinkDeletionMessage.message,
      )
    )
  }

  test("handle /list command successfully with links") {
    val presenter = createPresenter()
    val msg = Message(
      messageId = 1,
      date = 0,
      chat = Chat(id = testChatId, "private"),
      text = Some("/list")
    )

    val linkList = List(
      LinkResponse(id = 1, url = testUrl, filters = Nil, tags = Nil)
    )

    val jsonBody: String = linkList.asJson.noSpaces

    val successfulResponse = Response(
      body = Right(jsonBody),
      code = StatusCode.Ok,
      statusText = "OK",
      headers = Nil,
      history = Nil,
      request = null
    )

    when(mockDialogRepo.get(testChatId)).thenReturn(IO.pure(None))
    when(mockBackend.send(any())).thenReturn(IO.pure(successfulResponse))
    when(mockApi.execute(any())).thenReturn(IO.unit)

    presenter.onMessage(msg).unsafeRunSync()

    verify(mockApi).execute(
      Methods.sendMessage(
        ChatIntId(testChatId),
        ResponseMessage.LinkRepresentationMessage(linkList.head).message,
      )
    )
  }

  test("handle /list command successfully with empty link list") {
    val presenter = createPresenter()
    val msg = Message(
      messageId = 1,
      date = 0,
      chat = Chat(id = testChatId, "private"),
      text = Some("/list")
    )

    val jsonBody = List.empty[LinkResponse].asJson.noSpaces
    val successfulResponse = Response(
      body = Right(jsonBody),
      code = StatusCode.Ok,
      statusText = "OK",
      headers = Nil,
      history = Nil,
      request = null
    )

    when(mockDialogRepo.get(testChatId)).thenReturn(IO.pure(None))
    when(mockBackend.send(any())).thenReturn(IO.pure(successfulResponse))
    when(mockApi.execute(any())).thenReturn(IO.unit)

    presenter.onMessage(msg).unsafeRunSync()

    verify(mockApi).execute(
      Methods.sendMessage(
        ChatIntId(testChatId),
        ResponseMessage.EmptyLinkListMessage.message,
      )
    )
  }

  test("handle /list command with invalid json from scrapper") {
    val presenter = createPresenter()
    val msg = Message(
      messageId = 1,
      date = 0,
      chat = Chat(id = testChatId, "private"),
      text = Some("/list")
    )

    val invalidJson  = """{"invalid": "data"}"""
    val parseJsonRes = invalidJson.jsonAs[List[LinkResponse]]

    val successfulResponse = Response(
      body = Right(invalidJson),
      code = StatusCode.Ok,
      statusText = "OK",
      headers = Nil,
      history = Nil,
      request = null
    )

    when(mockDialogRepo.get(testChatId)).thenReturn(IO.pure(None))
    when(mockBackend.send(any())).thenReturn(IO.pure(successfulResponse))
    when(mockApi.execute(any())).thenReturn(IO.unit)

    presenter.onMessage(msg).unsafeRunSync()

    parseJsonRes match
      case Left(error) =>
        verify(mockApi).execute(
          Methods.sendMessage(
            ChatIntId(testChatId),
            ResponseMessage.FailedParseJsonMessage(error.getMessage()).message,
          )
        )
      case Right(_) => fail("Failed test: didn't catch json parse error")
  }

  test("handle /help command successfully") {
    val presenter = createPresenter()
    val msg = Message(
      messageId = 1,
      date = 0,
      chat = Chat(id = testChatId, "private"),
      text = Some("/help")
    )

    val helpText =
      "/untrack - Удалить URL из трекинга. Использование: /untrack <URL>\n/track - Добавить URL в трекинг. Использование: /track <URL>\n/help - Показать список доступных команд\n/start - Начать работу с ботом\n/list - Показать список отслеживаемых ссылок"

    when(mockDialogRepo.get(testChatId)).thenReturn(IO.pure(None))
    when(mockApi.execute(any())).thenReturn(IO.unit)

    when(presenter.helpReference(testChatId, helpText)).thenReturn(IO.unit)

    presenter.onMessage(msg).unsafeRunSync()

    verify(mockApi).execute(
      Methods.sendMessage(
        ChatIntId(testChatId),
        ResponseMessage.HelpReferenceMessage(helpText).message
      )
    )
  }

  test("handle unknown command") {
    val presenter = createPresenter()
    val msg = Message(
      messageId = 1,
      date = 0,
      chat = Chat(id = testChatId, "private"),
      text = Some("/unknown")
    )

    when(mockDialogRepo.get(testChatId)).thenReturn(IO.pure(None))

    when(mockApi.execute(any())).thenReturn(IO.unit)

    presenter.onMessage(msg).unsafeRunSync()

    verify(mockApi).execute(
      Methods.sendMessage(
        ChatIntId(testChatId),
        ResponseMessage.UnknownCommandMessage.message
      )
    )
  }

  test("handle non-command message") {
    val presenter = createPresenter()
    val msg = Message(
      messageId = 1,
      date = 0,
      chat = Chat(id = testChatId, "private"),
      text = Some("hello")
    )

    when(mockDialogRepo.get(testChatId)).thenReturn(IO.pure(None))

    when(mockApi.execute(any())).thenReturn(IO.unit)

    presenter.onMessage(msg).unsafeRunSync()

    verify(mockApi).execute(
      Methods.sendMessage(
        ChatIntId(testChatId),
        ResponseMessage.NotCommandMessage.message
      )
    )
  }
}
