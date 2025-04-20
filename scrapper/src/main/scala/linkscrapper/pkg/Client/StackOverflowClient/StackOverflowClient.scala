package linkscrapper.pkg.Client.StackOverflowClient

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

import cats.effect.*
import cats.implicits.*
import cats.Monad

import sttp.client3.*
import tethys._
import tethys.jackson._

import linkscrapper.pkg.Client.{LinkClient, LinkUpdate}

final case class StackUser(display_name: String) derives JsonReader

final case class StackAnswer(
  creation_date: Long,
  body: String,
  owner: StackUser
) derives JsonReader

final case class StackComment(
  creation_date: Long,
  body: String,
  owner: StackUser
) derives JsonReader

final case class StackAnswerResponse(items: List[StackAnswer]) derives JsonReader
final case class StackCommentResponse(items: List[StackComment]) derives JsonReader

trait StackOverflowClient[F[_]: Monad] extends LinkClient[F]:
  private val clientPrefix = "https://stackoverflow.com/questions/"

  override def getUpdates(url: String, since: Instant): F[Either[String, List[LinkUpdate]]] =
    val questionIdOpt = url.stripPrefix(clientPrefix).takeWhile(_.isDigit).toIntOption

    questionIdOpt match
      case Some(questionId) =>
        val answers = getAnswers(questionId, since)
        val comments = getComments(questionId, since)

        (answers, comments).mapN {
          case (Right(ans), Right(coms)) =>
            val updates = (ans ++ coms).sortBy(_.updatedAt)
            Right(updates)
          case (Left(err), _) => Left(err)
          case (_, Left(err)) => Left(err)
        }

      case None =>
        Monad[F].pure(Left("Invalid StackOverflow URL"))

  def getAnswers(questionId: Int, since: Instant): F[Either[String, List[LinkUpdate]]]
  def getComments(questionId: Int, since: Instant): F[Either[String, List[LinkUpdate]]]

object StackOverflowClient:
  final private class Impl(client: SttpBackend[IO, Any]) extends StackOverflowClient[IO]:
    private val baseUrl = "https://api.stackexchange.com/2.3"

    private def toInstant(epoch: Long): Instant =
      Instant.ofEpochSecond(epoch)

    private def toUpdate(url: String, text: String, createdAt: Long, user: StackUser, kind: String): LinkUpdate =
      LinkUpdate(
        url = url,
        updatedAt = toInstant(createdAt),
        description = s"$kind by ${user.display_name} at ${toInstant(createdAt)}\n\n${text.take(200)}"
      )

    override def getAnswers(questionId: Int, since: Instant): IO[Either[String, List[LinkUpdate]]] =
      val request = basicRequest
        .get(uri"$baseUrl/questions/$questionId/answers?order=desc&sort=creation&site=stackoverflow&filter=withbody")
      
      client.send(request).map { resp =>
        resp.body match
          case Right(json) =>
            println(json)
            json.jsonAs[StackAnswerResponse] match
              case Right(response) =>
                val updates = response.items.filter(a => toInstant(a.creation_date).isAfter(since))
                Right(updates.map(a =>
                  toUpdate(
                    url = s"https://stackoverflow.com/a/${a.creation_date}",
                    text = a.body,
                    createdAt = a.creation_date,
                    user = a.owner,
                    kind = "Answer"
                  )
                ))
              case Left(err) => Left(s"JSON decode error (answers): $err")
          case Left(err) =>
            Left(s"HTTP error (answers): $err")
      }

    def getComments(questionId: Int, since: Instant): IO[Either[String, List[LinkUpdate]]] =
      val request = basicRequest
        .get(uri"$baseUrl/questions/$questionId/comments?order=desc&sort=creation&site=stackoverflow&filter=withbody")

      client.send(request).map { resp =>
        resp.body match
          case Right(json) =>
            json.jsonAs[StackCommentResponse] match
              case Right(response) =>
                val updates = response.items.filter(a => toInstant(a.creation_date).isAfter(since))
                Right(updates.map(c =>
                  toUpdate(
                    url = s"https://stackoverflow.com/q/$questionId", // Комменты не имеют отдельного URL
                    text = c.body,
                    createdAt = c.creation_date,
                    user = c.owner,
                    kind = "Comment"
                  )
                ))
              case Left(err) => Left(s"JSON decode error (comments): $err")
          case Left(err) =>
            Left(s"HTTP error (comments): $err")
      }

  def make(client: SttpBackend[IO, Any]): StackOverflowClient[IO] =
    Impl(client)

// import cats.effect.IO 
// import sttp.client3.httpclient.cats.HttpClientCatsBackend
// import sttp.client3.*
// object Main extends IOApp:

//   override def run(args: List[String]): IO[ExitCode] =
//     val testUrl = "https://stackoverflow.com/questions/75945055/how-to-use-scala-to-convert-json-to-case-class" // можно заменить на любой другой валидный вопрос
//     val since = Instant.now().minusSeconds(60 * 60 * 24 * 745)

//     val backendResource = HttpClientCatsBackend.resource[IO]()

//     backendResource.use { backend =>
//       val client = StackOverflowClient.make(backend)

//       client.getUpdates(testUrl, since).flatMap {
//         case Right(updates) =>
//           IO.println(s"✅ Found ${updates.length} updates:") *>
//           updates.traverse_(u => IO.println(s"\n---\nURL: ${u.url}\nTime: ${u.updatedAt}\nDescription:\n${u.description}"))

//         case Left(error) =>
//           IO.println(s"❌ Error: $error")
//       }
//     }.as(ExitCode.Success)
