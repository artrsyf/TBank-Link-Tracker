package linkscrapper.pkg.Client.GitHubClient

import cats.effect.*
import cats.syntax.functor._
import cats.effect.kernel.Async
import cats.Parallel
import cats.effect.unsafe.implicits.global

import sttp.client3._
import sttp.client3.impl.cats._
import sttp.client3.httpclient.cats.HttpClientCatsBackend
import sttp.tapir._
import sttp.tapir.json.tethysjson

import tethys._
import tethys.jackson._

final case class GitHubRepo(updated_at: String) derives JsonReader

trait GitHubClient[F[_]]:
  def getRepoUpdate(owner: String, repo: String): F[Either[String, GitHubRepo]]

object GitHubClient:
  final private class Impl(
    client: SttpBackend[IO, Any],
  ) extends GitHubClient[IO]:
    override def getRepoUpdate(owner: String, repo: String): IO[Either[String, GitHubRepo]] = 
      val request = basicRequest
        .get(uri"https://api.github.com/repos/$owner/$repo")
    
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

// object GitHubClient:
//   def getRepoUpdate[F[_]: Async](client: SttpBackend[F, Any], owner: String, repo: String): F[Either[String, GitHubRepo]] =
//     val request = basicRequest
//       .get(uri"https://api.github.com/repos/$owner/$repo")
    
//     client.send(request).fmap { response =>
//       response.body match
//         case Right(json) =>
//           json.jsonAs[GitHubRepo] match
//             case Right(repo) => Right(repo)
//             case Left(err)   => Left(s"JSON decode error: $err")
        
//         case Left(error) =>
//             Left(s"HTTP error: $error")
//     }

@main def run(): Unit =
  HttpClientCatsBackend.resource[IO]().use { backend =>
    val ghClient = GitHubClient.make(backend)
    ghClient.getRepoUpdate("typelevel", "cats-effect")
      .flatMap {
        case Right(repo) =>
          IO.println(s"Last update: ${repo.updated_at}")
        case Left(error) =>
          IO.println(s"Error: $error")
      }
  }.unsafeRunSync() 
