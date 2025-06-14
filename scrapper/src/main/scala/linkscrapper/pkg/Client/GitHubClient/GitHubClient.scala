package linkscrapper.pkg.Client.GitHubClient

import java.time.Instant

import cats.effect.*
import cats.Monad
import cats.syntax.functor._
import cats.implicits._

import sttp.client3._

import tethys._
import tethys.jackson._

import linkscrapper.pkg.Client.{LinkClient, LinkUpdate}

final case class GitHubUser(
  login: String
) derives JsonReader

final case class GitHubItem(
  title: String,
  user: GitHubUser,
  created_at: String,
  body: Option[String],
) derives JsonReader

trait GitHubClient[F[_]: Monad] extends LinkClient[F]:
  private val clientPrefix: String = "https://github.com/"

  override def getUpdates(url: String, since: Instant): F[Either[String, List[LinkUpdate]]] =
    val requestParts = url.stripPrefix(clientPrefix).split("/")
    if (requestParts.length >= 2) {
      val owner = requestParts(0)
      val repo  = requestParts(1)

      val prUpdates    = getPullRequests(owner, repo, since)
      val issueUpdates = getIssues(owner, repo, since)

      for {
        prs    <- prUpdates
        issues <- issueUpdates
      } yield {
        (prs, issues) match {
          case (Right(prList), Right(issueList)) =>
            Right((prList ++ issueList).map(toUpdate(owner, repo)))
          case (Left(err), _) => Left(err)
          case (_, Left(err)) => Left(err)
        }
      }
    } else {
      Monad[F].pure(Left("Invalid GitHub URL"))
    }

  private def toUpdate(owner: String, repo: String)(gitHubItem: GitHubItem): LinkUpdate = {
    val preview = gitHubItem.body.getOrElse("").take(200)
    LinkUpdate(
      url = s"https://github.com/$owner/$repo/${gitHubItem.title}",
      updatedAt = Instant.parse(gitHubItem.created_at),
      description =
        s"Title: ${gitHubItem.title}\nUser: ${gitHubItem.user.login}\nDescription: ${gitHubItem.body.getOrElse("")}"
    )
  }

  def getPullRequests(owner: String, repo: String, since: Instant): F[Either[String, List[GitHubItem]]]
  def getIssues(owner: String, repo: String, since: Instant): F[Either[String, List[GitHubItem]]]

object GitHubClient:
  final private class Impl(
    client: SttpBackend[IO, Any],
  ) extends GitHubClient[IO]:
    private val gitHubApiUrl = "https://api.github.com/repos"

    override def getPullRequests(owner: String, repo: String, since: Instant): IO[Either[String, List[GitHubItem]]] =
      val request = basicRequest
        .get(uri"${gitHubApiUrl}/$owner/$repo/pulls?since=${since.toString}")

      client.send(request).fmap { response =>
        response.body match
          case Right(json) =>
            json.jsonAs[List[GitHubItem]] match
              case Right(prs) => Right(prs)
              case Left(err)  => Left(s"JSON decode error: $err")
          case Left(error) =>
            Left(s"HTTP error: $error")
      }

    override def getIssues(owner: String, repo: String, since: Instant): IO[Either[String, List[GitHubItem]]] =
      val request = basicRequest
        .get(uri"${gitHubApiUrl}/$owner/$repo/issues?since=${since.toString}")

      client.send(request).fmap { response =>
        response.body match
          case Right(json) =>
            json.jsonAs[List[GitHubItem]] match
              case Right(issues) => Right(issues)
              case Left(err)     => Left(s"JSON decode error: $err")
          case Left(error) =>
            Left(s"HTTP error: $error")
      }

  def make(client: SttpBackend[IO, Any]): GitHubClient[IO] =
    Impl(client)

// import cats.effect.*
// import sttp.client3.httpclient.cats.HttpClientCatsBackend
// import java.time.Instant

// object Main extends IOApp:

//   override def run(args: List[String]): IO[ExitCode] =
//     val since = Instant.now() // последние 7 дней
//     val testUrl = "https://github.com/tensorflow/tensorflow" // ⚠️ важный момент — это не API URL

//     val backendResource = HttpClientCatsBackend.resource[IO]()

//     backendResource.use { backend =>
//       val client = GitHubClient.make(backend)

//       client.getUpdates(testUrl, since).flatMap {
//         case Right(updates) =>
//           IO.println(s"\n✅ Found ${updates.length} updates:") *>
//           updates.traverse_(u => IO.println(s"\n---\nURL: ${u.url}\nTime: ${u.updatedAt}\nDescription:\n${u.description.take(300)}"))

//         case Left(error) =>
//           IO.println(s"\n❌ Error: $error")
//       }
//     }.as(ExitCode.Success)
