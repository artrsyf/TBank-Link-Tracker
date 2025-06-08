package linkscrapper

import cats.effect.{ExitCode, IO, IOApp}
import com.comcast.ip4s.{Host, Port}
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Router
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.Logger
import sttp.client3.httpclient.cats.HttpClientCatsBackend
import sttp.tapir.server.http4s.Http4sServerInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter

import linkscrapper.chat.delivery.http.ChatHandler
import linkscrapper.config.AppConfig
import linkscrapper.link.delivery.http.LinkHandler
import linkscrapper.pkg.Client.LinkClient
import linkscrapper.pkg.Client.GitHubClient.GitHubClient
import linkscrapper.pkg.Client.StackOverflowClient.StackOverflowClient
import linkscrapper.pkg.Scheduler.QuartzScheduler
import linkscrapper.wiring.{Repositories, Usecases}

object Main extends IOApp:
  given logger: Logger[IO] = Slf4jLogger.getLogger[IO]

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
        logger,
      )
      endpoints <-
        IO {
          List(
            ChatHandler.make(usecases.chatUsecase),
            LinkHandler.make(usecases.chatUsecase, usecases.linkUsecase, logger),
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
          .withHost(Host.fromString("0.0.0.0").get) // docker fix?
          .withPort(port)
          .withHttpApp(Router("/" -> routes).orNotFound)
          .build
          .evalTap(server =>
            Logger[IO].info(
              s"Server available at http://0.0.0.0:${server.address.getPort}"
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
