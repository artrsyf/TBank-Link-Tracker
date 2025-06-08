package linkscrapper.chat.usecase

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.mockito.Mockito.*
import org.mockito.ArgumentMatchers.anyLong
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar

import linkscrapper.chat.domain.entity.{Chat, ChatError}
import linkscrapper.chat.repository.ChatRepository

class ChatUsecaseSpec extends AnyFunSuite with Matchers with MockitoSugar {

  private val testChatId = 123L
  private val testChat   = Chat(testChatId)

  test("create should return Right when chat does not exist") {
    val mockRepo = mock[ChatRepository[IO]]
    when(mockRepo.getById(testChatId)).thenReturn(IO.pure(None))
    when(mockRepo.create(testChat)).thenReturn(IO.unit)

    val usecase = ChatUsecase.make(mockRepo)
    val result  = usecase.create(testChat).unsafeRunSync()

    result shouldBe Right(testChat)
    verify(mockRepo).create(testChat)
  }

  test("create should return Left when chat already exists") {
    val mockRepo = mock[ChatRepository[IO]]
    when(mockRepo.getById(testChatId)).thenReturn(IO.pure(Some(testChat)))

    val usecase = ChatUsecase.make(mockRepo)
    val result  = usecase.create(testChat).unsafeRunSync()

    result shouldBe Left(ChatError.ErrAlreadyRegistered)
    verify(mockRepo, never()).create(testChat)
  }

  test("delete should return Right when delete succeeds") {
    val mockRepo = mock[ChatRepository[IO]]
    when(mockRepo.delete(testChatId)).thenReturn(IO.unit)

    val usecase = ChatUsecase.make(mockRepo)
    val result  = usecase.delete(testChatId).unsafeRunSync()

    result shouldBe Right(())
    verify(mockRepo).delete(testChatId)
  }

  test("delete should return Left when delete fails") {
    val mockRepo = mock[ChatRepository[IO]]
    when(mockRepo.delete(testChatId)).thenReturn(IO.raiseError(new Exception("fail")))

    val usecase = ChatUsecase.make(mockRepo)
    val result  = usecase.delete(testChatId).unsafeRunSync()

    result shouldBe Left(ChatError.ErrDeletionInvalidChat)
  }

  test("check should return true if chat exists") {
    val mockRepo = mock[ChatRepository[IO]]
    when(mockRepo.getById(testChatId)).thenReturn(IO.pure(Some(testChat)))

    val usecase = ChatUsecase.make(mockRepo)
    val result  = usecase.check(testChatId).unsafeRunSync()

    result shouldBe true
  }

  test("check should return false if chat does not exist") {
    val mockRepo = mock[ChatRepository[IO]]
    when(mockRepo.getById(testChatId)).thenReturn(IO.pure(None))

    val usecase = ChatUsecase.make(mockRepo)
    val result  = usecase.check(testChatId).unsafeRunSync()

    result shouldBe false
  }
}
