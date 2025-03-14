package linkscrapper.pkg.Client.StackOverflowClient

import java.time.Instant

import cats.effect.*
import cats.Monad
import cats.syntax.functor._

import sttp.client3._

import tethys._
import tethys.jackson._

import linkscrapper.pkg.Client.LinkClient

final case class StackOverflowQuestion(
  question_id: Long, 
  title: String, 
  link: String, 
  last_activity_date: Long
) derives JsonReader

final case class StackOverflowResponse(items: List[StackOverflowQuestion]) derives JsonReader

trait StackOverflowClient[F[_]: Monad] extends LinkClient[F]:
  private val clientPrefix: String = "https://stackoverflow.com/questions/"

  override def getLastUpdate(url: String): F[Either[String, Instant]] = {
    val requestParts = url.stripPrefix(clientPrefix).split("/")
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
    private def stackOverflowApiUrl(id: Long) = 
      s"https://api.stackexchange.com/2.3/questions/$id?site=stackoverflow"

    override def getQuestionById(id: Long): IO[Either[String, StackOverflowQuestion]] = {
      val request = basicRequest
        .get(uri"${stackOverflowApiUrl}")

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