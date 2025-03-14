package linkscrapper.pkg.Client.GitHubClient

import java.time.Instant

import cats.effect.*
import cats.Monad
import cats.syntax.functor._

import sttp.client3._

import tethys._
import tethys.jackson._

import linkscrapper.pkg.Client.LinkClient

final case class GitHubRepo(updated_at: String) derives JsonReader

trait GitHubClient[F[_]: Monad] extends LinkClient[F]:
  private val clientPrefix: String = "https://github.com/"

  override def getLastUpdate(url: String): F[Either[String, Instant]] = 
    val requestParts = url.stripPrefix(clientPrefix).split("/")
    if (requestParts.length >= 2) {
      val owner = requestParts(0)
      val repo = requestParts(1)

      getRepoUpdate(owner, repo).map {
        case Right(repo) => Right(Instant.parse(repo.updated_at))
        case Left(error) => Left(error)
      }
    } else {
      Monad[F].pure(Left("Invalid GitHub URL"))
    }

  def getRepoUpdate(owner: String, repo: String): F[Either[String, GitHubRepo]]

object GitHubClient:
  final private class Impl(
    client: SttpBackend[IO, Any],
  ) extends GitHubClient[IO]:
    private val gitHubApiUrl = "https://api.github.com/repos"

    override def getRepoUpdate(owner: String, repo: String): IO[Either[String, GitHubRepo]] = 
      val request = basicRequest
        .get(uri"${gitHubApiUrl}/$owner/$repo")
    
      client.send(request).fmap { response =>
        response.body match
          case Right(json) =>
            json.jsonAs[GitHubRepo] match
              case Right(repo) => Right(repo)
              case Left(err)   => Left(s"JSON decode error: $err")
          
          case Left(error) =>
              Left(s"HTTP error: $error")
      }
  
  def make(client: SttpBackend[IO, Any]): GitHubClient[IO] = 
    Impl(client)
