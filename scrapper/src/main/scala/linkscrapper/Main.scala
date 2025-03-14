package linkscrapper

import cats.effect.{ExitCode, IO, IOApp}
import com.comcast.ip4s.{Host, Port}
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Router
import sttp.client3.httpclient.cats.HttpClientCatsBackend
import sttp.tapir.server.http4s.Http4sServerInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter

import linkscrapper.Chat.delivery.http.ChatHandler
import linkscrapper.config.AppConfig
import linkscrapper.Link.delivery.http.LinkHandler
import linkscrapper.pkg.Client.LinkClient
import linkscrapper.pkg.Client.GitHubClient.GitHubClient
import linkscrapper.pkg.Client.StackOverflowClient.StackOverflowClient
import linkscrapper.pkg.Scheduler.QuartzScheduler
import linkscrapper.wiring.{Repositories, Usecases}

object Main extends IOApp:
  override def run(args: List[String]): IO[ExitCode] =
    for {
      appConfig <- AppConfig.load

      backend             <- HttpClientCatsBackend.resource[IO]().use(IO.pure)
      githubClient        <- IO(GitHubClient.make(backend))
      stackoverflowClient <- IO(StackOverflowClient.make(backend))

      clients = Map[String, LinkClient[IO]](
        "https://github.com/"        -> githubClient,
        "https://stackoverflow.com/" -> stackoverflowClient,
      )

      repositories <- Repositories.make
      usecases = Usecases.make(repositories, clients.keys.toList)

      scheduler = QuartzScheduler(
        appConfig.scheduler,
        usecases.linkUsecase,
        clients,
        backend,
      )
      endpoints <-
        IO {
          List(
            ChatHandler.make(usecases.chatUsecase),
            LinkHandler.make(usecases.chatUsecase, usecases.linkUsecase),
          ).flatMap(_.endpoints)
        }

      swaggerEndpoint = SwaggerInterpreter().fromServerEndpoints[IO](
        endpoints,
        "Scrapper API",
        "1.0.0"
      )

      routes = Http4sServerInterpreter[IO]()
        .toRoutes(swaggerEndpoint ++ endpoints)

      port <- getPortSafe(appConfig.server.port)
      _ <- IO.race(
        EmberServerBuilder
          .default[IO]
          .withHost(Host.fromString("localhost").get)
          .withPort(port)
          .withHttpApp(Router("/" -> routes).orNotFound)
          .build
          .evalTap(server =>
            IO.println(
              s"Server available at http://localhost:${server.address.getPort}"
            )
          )
          .useForever,
        scheduler.runScheduler
      )
    } yield ExitCode.Success

  private def getPortSafe(v: Int): IO[Port] =
    IO.delay(
      Port.fromInt(v).toRight(IllegalArgumentException("Invalid port value"))
    ).rethrow
