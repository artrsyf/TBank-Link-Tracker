package linkscrapper.pkg.Client.StackOverflowClient

import linkscrapper.pkg.Client.LinkClient

import cats.effect.*
import cats.syntax.functor._
import cats.Monad

import sttp.client3._
import sttp.client3.impl.cats._
import sttp.client3.httpclient.cats.HttpClientCatsBackend
import sttp.tapir._
import tethys._
import tethys.jackson._

import cats.effect.unsafe.implicits.global
import java.time.Instant

final case class StackOverflowResponse(items: List[StackOverflowQuestion]) derives JsonReader

final case class StackOverflowQuestion(question_id: Long, title: String, link: String, last_activity_date: Long) derives JsonReader

trait StackOverflowClient[F[_]: Monad] extends LinkClient[F]:
  override def getLastUpdate(url: String): F[Either[String, Instant]] = {
    val requestParts = url.stripPrefix("https://stackoverflow.com/questions/").split("/")
    if (requestParts.nonEmpty) {
      val questionId = requestParts(0).toLong
      getQuestionById(questionId).map {
        case Right(question) => Right(Instant.ofEpochSecond(question.last_activity_date))
        case Left(error)    => Left(error)
      }
    } else {
      Monad[F].pure(Left("Invalid StackOverflow URL"))
    }
  }
    
  def getQuestionById(id: Long): F[Either[String, StackOverflowQuestion]]

object StackOverflowClient:
  final private class Impl(
    client: SttpBackend[IO, Any]
  ) extends StackOverflowClient[IO]:
    override def getQuestionById(id: Long): IO[Either[String, StackOverflowQuestion]] = {
      val request = basicRequest
        .get(uri"https://api.stackexchange.com/2.3/questions/$id?site=stackoverflow")

      client.send(request).fmap { response =>
        response.body match
          case Right(json) =>
            json.jsonAs[StackOverflowResponse] match
              case Right(response) if response.items.nonEmpty => Right(response.items.head)
              case Right(_) => Left("Question not found")
              case Left(err) => Left(s"JSON decode error: $err")
          
          case Left(error) =>
            Left(s"HTTP error: $error")
      }
  }

  def make(client: SttpBackend[IO, Any]): StackOverflowClient[IO] = 
    new Impl(client)

@main def run(): Unit =
  HttpClientCatsBackend.resource[IO]().use { backend =>
    val cl = StackOverflowClient.make(backend)
    cl.getQuestionById(26380420)
      .flatMap {
        case Right(question) =>
          val instant = Instant.ofEpochSecond(question.last_activity_date)
          
          val formattedDate = instant.atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            
          IO.println(s"Last update: $formattedDate")
          
        case Left(error) =>
          IO.println(s"Error: $error")
      }
  }.unsafeRunSync()

