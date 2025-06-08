package linkscrapper.link.repository.InMemory

import cats.effect.{IO, Ref}
import cats.effect.unsafe.implicits.global
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import linkscrapper.link.domain.dto._
import linkscrapper.link.domain.entity
import linkscrapper.link.domain.model
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.time.Instant

class InMemoryLinkRepositorySpec extends AnyFunSuite with Matchers {
  private val testChatId  = 100L
  private val testUrl     = "https://example.com"
  private val testTags    = List("tag1", "tag2")
  private val testFilters = List("filter1")

  private def newAddRequest = AddLinkRequest(testUrl, testTags, testFilters)
  private def newLinkEntity = LinkAddRequestToEntity(newAddRequest, testChatId)

  private def createRepo(
      linkState: Map[String, model.Link] = Map.empty,
      userLinkState: Map[(Long, Long), model.UserLink] = Map.empty
  ): IO[InMemoryLinkRepository] = for {
    linkRef     <- Ref.of[IO, Map[String, model.Link]](linkState)
    userLinkRef <- Ref.of[IO, Map[(Long, Long), model.UserLink]](userLinkState)
    logger      <- Slf4jLogger.create[IO]
  } yield new InMemoryLinkRepository(linkRef, userLinkRef, logger)

  test("createUserLink creates new link and user link") {
    val result = for {
      repo      <- createRepo()
      link      <- repo.createUserLink(newLinkEntity)
      allLinks  <- repo.getLinks
      userLinks <- repo.getUserLinksByChatId(testChatId)
    } yield (link, allLinks, userLinks)

    val (link, allLinks, userLinks) = result.unsafeRunSync()

    link.url shouldBe testUrl
    link.chatId shouldBe testChatId
    allLinks.exists(_.url == testUrl) shouldBe true
    userLinks.exists(_.url == testUrl) shouldBe true
  }

  test("createUserLink uses existing link if already present") {
    val existingLink = model.Link(1L, testUrl, Instant.now())
    val repoIO       = createRepo(linkState = Map(testUrl -> existingLink))

    val result = for {
      repo <- repoIO
      link <- repo.createUserLink(newLinkEntity)
    } yield link

    val link = result.unsafeRunSync()
    link.id shouldBe 1L
    link.url shouldBe testUrl
  }

  test("deleteUserLink deletes user link and returns entity") {
    val modelLink     = model.Link(1L, testUrl, Instant.now())
    val modelUserLink = model.UserLink(testChatId, 1L, testTags, testFilters, Instant.now())
    val repoIO = createRepo(
      linkState = Map(testUrl -> modelLink),
      userLinkState = Map((testChatId, 1L) -> modelUserLink)
    )

    val result = for {
      repo      <- repoIO
      deleted   <- repo.deleteUserLink(testUrl, testChatId)
      remaining <- repo.getUserLinksByChatId(testChatId)
    } yield (deleted, remaining)

    val (deletedOpt, remainingLinks) = result.unsafeRunSync()
    deletedOpt.isDefined shouldBe true
    deletedOpt.get.url shouldBe testUrl
    remainingLinks shouldBe empty
  }

  test("getUserLinksByLinkUrl returns all user links for link") {
    val modelLink = model.Link(1L, testUrl, Instant.now())
    val userLinks = Map(
      (testChatId, 1L) -> model.UserLink(testChatId, 1L, testTags, testFilters, Instant.now()),
      (999L, 1L)       -> model.UserLink(999L, 1L, List("tag3"), List(), Instant.now())
    )

    val repoIO = createRepo(
      linkState = Map(testUrl -> modelLink),
      userLinkState = userLinks
    )

    val result = for {
      repo  <- repoIO
      links <- repo.getUserLinksByLinkUrl(testUrl)
    } yield links

    val links = result.unsafeRunSync()
    links.map(_.chatId).toSet shouldBe Set(testChatId, 999L)
  }

  test("update updates an existing link") {
    val modelLink = model.Link(1L, testUrl, Instant.now())
    val repoIO    = createRepo(linkState = Map(testUrl -> modelLink))

    val result = for {
      repo    <- repoIO
      updated <- repo.update(modelLink.copy(updatedAt = Instant.EPOCH))
    } yield updated

    val updatedOpt = result.unsafeRunSync()
    updatedOpt.isDefined shouldBe true
    updatedOpt.get.url shouldBe testUrl
    updatedOpt.get.updatedAt should not be Instant.EPOCH
  }

  test("update returns None if link not found") {
    val repoIO = createRepo()

    val result = for {
      repo    <- repoIO
      updated <- repo.update(model.Link(1L, testUrl, Instant.now()))
    } yield updated

    result.unsafeRunSync() shouldBe None
  }

  test("getLinks returns all stored links") {
    val links = List("a", "b").zipWithIndex.map { case (url, idx) =>
      url -> model.Link(idx + 1, url, Instant.now())
    }.toMap

    val result = for {
      repo <- createRepo(linkState = links)
      all  <- repo.getLinks
    } yield all

    val all = result.unsafeRunSync()
    all.map(_.url).toSet shouldBe Set("a", "b")
  }

  test("getUserLinksByChatId returns user-specific links") {
    val link      = model.Link(1L, testUrl, Instant.now())
    val userLink  = model.UserLink(testChatId, 1L, testTags, testFilters, Instant.now())
    val userLink2 = model.UserLink(99L, 1L, List("other"), List(), Instant.now())

    val result = for {
      repo <- createRepo(
        linkState = Map(testUrl -> link),
        userLinkState = Map(
          (testChatId, 1L) -> userLink,
          (99L, 1L)        -> userLink2
        )
      )
      links <- repo.getUserLinksByChatId(testChatId)
    } yield links

    val links = result.unsafeRunSync()
    links.length shouldBe 1
    links.head.chatId shouldBe testChatId
  }
}
