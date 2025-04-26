package linkscrapper.chat.repository.InMemory

import java.time.Instant
import cats.effect.{IO, Ref}
import cats.effect.unsafe.implicits.global
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import linkscrapper.chat.domain.entity.Chat
import linkscrapper.chat.domain.model
import linkscrapper.chat.domain.model.Chat as ModelChat
import linkscrapper.chat.repository.InMemory.InMemoryChatRepository
import linkscrapper.chat.domain.dto.ChatEntityToModel
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

class InMemoryChatRepositorySpec extends AnyFunSuite with Matchers {

  private val testChatId = 123L
  private val testEntity = Chat(testChatId)
  private val testInstant = Instant.now()

  private def createLogger: IO[Logger[IO]] = Slf4jLogger.create[IO]

  private def createInstance(ref: Ref[IO, Map[Long, ModelChat]]): IO[InMemoryChatRepository] =
    createLogger.map(logger => new InMemoryChatRepository(ref, logger))

  test("successfully create chat") {
    val result = (for {
      ref <- Ref.of[IO, Map[Long, ModelChat]](Map.empty)
      repo <- createInstance(ref)
      created <- repo.create(testEntity)
      storage <- ref.get
    } yield (created, storage)).unsafeRunSync()

    result match {
      case (created, storage) =>
        storage.get(testChatId) shouldBe Some(created)
        created.chatId shouldBe testChatId
        created.createdAt should not be null
    }
  }

  test("successfully delete chat") {
    val testModel = ChatEntityToModel(testEntity, testInstant)
    val initialData = Map(testChatId -> testModel)

    val result = (for {
      ref <- Ref.of[IO, Map[Long, ModelChat]](initialData)
      repo <- createInstance(ref)
      _ <- repo.delete(testChatId)
      storage <- ref.get
    } yield storage).unsafeRunSync()

    result.get(testChatId) shouldBe None
  }

  test("return existing chat") {
    val testModel = ChatEntityToModel(testEntity, testInstant)
    val initialData = Map(testChatId -> testModel)

    val result = (for {
      ref <- Ref.of[IO, Map[Long, ModelChat]](initialData)
      repo <- createInstance(ref)
      found <- repo.getById(testChatId)
    } yield found).unsafeRunSync()

    result shouldBe Some(testModel)
  }

  test("return None for non-existent chat") {
    val result = (for {
      ref <- Ref.of[IO, Map[Long, ModelChat]](Map.empty)
      repo <- createInstance(ref)
      found <- repo.getById(999L)
    } yield found).unsafeRunSync()

    result shouldBe None
  }
}
