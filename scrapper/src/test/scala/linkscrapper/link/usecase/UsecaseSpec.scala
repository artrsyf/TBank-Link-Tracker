package linkscrapper.link.usecase

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.mockito.Mockito.*
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.typelevel.log4cats.Logger

import linkscrapper.link.domain.dto.*
import linkscrapper.link.domain.entity.{Link, LinkError}
import linkscrapper.link.domain.model
import linkscrapper.link.repository.LinkRepository

import java.time.Instant

class LinkUsecaseSpec extends AnyFunSuite with Matchers with MockitoSugar {

  private val testChatId = 42L
  private val testUrl    = "https://github.com"
  private val badUrl     = "https://notallowed.com"
  private val addRequest = AddLinkRequest(testUrl, List("tag1"), List("filter1"))

  private val linkModel = model.Link(1L, testUrl, Instant.now())
  private val userLinkModel = model.UserLink(testChatId, 1L, List("tag1"), List("filter1"), Instant.now())
  private val linkEntity = LinkModelToEntity(linkModel, userLinkModel)

  private def mockLogger = mock[Logger[IO]]
  private def mockRepo = mock[LinkRepository[IO]]
  private val allowedClients = List("https://github.com", "https://youtube.com")

  test("addUserLink returns Right when client is valid") {
    val repo = mock[LinkRepository[IO]]
    val logger = mock[Logger[IO]]
    val allowedClients = List("https://valid.com")
    val addRequest = AddLinkRequest("https://valid.com/page", List("tag"), List("filter"))
    val testChatId = 123L

    val expectedLink = LinkAddRequestToEntity(addRequest, testChatId)

    when(repo.createUserLink(any[Link])).thenReturn(IO.pure(expectedLink))

    val usecase = LinkUsecase.make(repo, allowedClients, logger)
    val result = usecase.addUserLink(addRequest, testChatId).unsafeRunSync()

    result match {
      case Right(link) =>
        link.url shouldBe expectedLink.url
        link.chatId shouldBe expectedLink.chatId
        link.tags shouldBe expectedLink.tags
        link.filters shouldBe expectedLink.filters
      case Left(error) => fail(s"Expected Right, got Left($error)")
    }
    verify(repo).createUserLink(any[Link])
  }

  test("addUserLink returns Left for invalid client") {
    val repo = mock[LinkRepository[IO]]
    val logger = mock[Logger[IO]]

    val allowedClients = List("allowed.com")
    val badUrl = "http://malicious.com"
    val addRequest = AddLinkRequest(badUrl, List("tag1"), List("filter1"))

    val usecase = LinkUsecase.make(repo, allowedClients, logger)

    val result = usecase.addUserLink(addRequest, testChatId).unsafeRunSync()

    result shouldBe Left(LinkError.ErrInvalidUrl)
  }

  test("removeUserLink returns Right if link exists") {
    val repo = mockRepo
    val logger = mockLogger

    when(repo.deleteUserLink(testUrl, testChatId)).thenReturn(IO.pure(Some(linkEntity)))

    val usecase = LinkUsecase.make(repo, allowedClients, logger)
    val result = usecase.removeUserLink(testUrl, testChatId).unsafeRunSync()

    result shouldBe Right(linkEntity)
  }

  test("removeUserLink returns Left if link not found") {
    val repo = mockRepo
    val logger = mockLogger

    when(repo.deleteUserLink(testUrl, testChatId)).thenReturn(IO.pure(None))

    val usecase = LinkUsecase.make(repo, allowedClients, logger)
    val result = usecase.removeUserLink(testUrl, testChatId).unsafeRunSync()

    result shouldBe Left(LinkError.ErrNoSuchLink)
  }

  test("updateLink returns Right when link exists") {
    val repo = mockRepo
    val logger = mockLogger

    when(repo.update(linkModel)).thenReturn(IO.pure(Some(linkModel)))

    val usecase = LinkUsecase.make(repo, allowedClients, logger)
    val result = usecase.updateLink(linkModel).unsafeRunSync()

    result shouldBe Right(linkModel)
  }

  test("updateLink returns Left when link not found") {
    val repo = mockRepo
    val logger = mockLogger

    when(repo.update(linkModel)).thenReturn(IO.pure(None))

    val usecase = LinkUsecase.make(repo, allowedClients, logger)
    val result = usecase.updateLink(linkModel).unsafeRunSync()

    result shouldBe Left(LinkError.ErrNoSuchLink)
  }

  test("getUserLinksByChatId returns list of user links") {
    val repo = mockRepo
    val logger = mockLogger

    val links = List(linkEntity)
    when(repo.getUserLinksByChatId(testChatId)).thenReturn(IO.pure(links))

    val usecase = LinkUsecase.make(repo, allowedClients, logger)
    val result = usecase.getUserLinksByChatId(testChatId).unsafeRunSync()

    result shouldBe links
  }

  test("getLinks returns list of model links") {
    val repo = mockRepo
    val logger = mockLogger

    val links = List(linkModel)
    when(repo.getLinks).thenReturn(IO.pure(links))

    val usecase = LinkUsecase.make(repo, allowedClients, logger)
    val result = usecase.getLinks.unsafeRunSync()

    result shouldBe links
  }

  test("getUserLinksByLinkUrl returns list of links by URL") {
    val repo = mockRepo
    val logger = mockLogger

    val links = List(linkEntity)
    when(repo.getUserLinksByLinkUrl(testUrl)).thenReturn(IO.pure(links))

    val usecase = LinkUsecase.make(repo, allowedClients, logger)
    val result = usecase.getUserLinksByLinkUrl(testUrl).unsafeRunSync()

    result shouldBe links
  }
}
