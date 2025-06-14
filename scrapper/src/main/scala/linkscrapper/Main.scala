package linkscrapper

import cats.effect.{ExitCode, IO, IOApp, Resource}
import doobie.hikari.HikariTransactor
import com.comcast.ip4s.{Host, Port}
import com.zaxxer.hikari.HikariConfig
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Router
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.Logger
import sttp.client3.httpclient.cats.HttpClientCatsBackend
import sttp.tapir.server.http4s.Http4sServerInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import fs2.kafka._

import linkscrapper.chat.delivery.http.ChatHandler
import linkscrapper.config.AppConfig
import linkscrapper.link.delivery.http.LinkHandler
import linkscrapper.pkg.Client.LinkClient
import linkscrapper.pkg.Client.GitHubClient.GitHubClient
import linkscrapper.pkg.Client.StackOverflowClient.StackOverflowClient
import linkscrapper.pkg.Scheduler.QuartzScheduler
import linkscrapper.wiring.{Repositories, Usecases}
import linkscrapper.pkg.Publisher.HttpPublisher.HttpPublisher
import linkscrapper.pkg.Publisher.KafkaPublisher.KafkaLinkPublisher
import linkscrapper.config.KafkaConfig

object Main extends IOApp:
  given logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  override def run(args: List[String]): IO[ExitCode] =
    for {
      appConfig <- AppConfig.load
      hikariConfig = makeHikariConfig(appConfig)
      hikariResource = createHikariTransactorResource(hikariConfig).map(Some(_))

      exitCode <- appConfig.db.inUse match
        case "postgres" => hikariResource.use(runApp(_, appConfig))
        case _          => runApp(None, appConfig)
    } yield exitCode

  private def makeHikariConfig(appConfig: AppConfig): HikariConfig =
    val config = new HikariConfig()
    config.setDriverClassName(appConfig.db.driver)
    config.setJdbcUrl(appConfig.db.url)
    config.setUsername(appConfig.db.user)
    config.setPassword(appConfig.db.password)
    config

  private def getPortSafe(v: Int): IO[Port] =
    IO.delay(Port.fromInt(v).toRight(new IllegalArgumentException("Invalid port value"))).rethrow

  private def createKafkaProducerResource(config: KafkaConfig): Resource[IO, KafkaProducer[IO, String, String]] =
    KafkaProducer.resource(ProducerSettings[IO, String, String].withProperties(config.properties))

  private def createHikariTransactorResource(config: HikariConfig): Resource[IO, HikariTransactor[IO]] =
    HikariTransactor.fromHikariConfig[IO](config)

  private def resources(
    hikariTransactor: Option[HikariTransactor[IO]],
    appConfig: AppConfig
  ): Resource[IO, (
    HikariTransactor[IO],
    LinkClient[IO],
    LinkClient[IO],
    sttp.client3.SttpBackend[IO, _],
    linkscrapper.pkg.Publisher.LinkPublisher[IO]
  )] = for {
    backend <- HttpClientCatsBackend.resource[IO]()

    githubClient        = GitHubClient.make(backend)
    stackoverflowClient = StackOverflowClient.make(backend)

    linkPublisher <- appConfig.transport.`type` match
      case "kafka" =>
        createKafkaProducerResource(appConfig.transport.kafka).map { producer =>
          new KafkaLinkPublisher(
            appConfig.transport.kafka.topic,
            producer,
            logger
          )
        }
      case "http" =>
        Resource.pure[IO, linkscrapper.pkg.Publisher.LinkPublisher[IO]](
          HttpPublisher(appConfig.transport.http.updatesHandlerEndpoint, backend, logger)
        )
      case other =>
        Resource.eval(IO.raiseError(new RuntimeException(s"Unsupported transport type: $other")))

  } yield (
    hikariTransactor.getOrElse(throw new IllegalStateException("Missing transactor")),
    githubClient,
    stackoverflowClient,
    backend,
    linkPublisher
  )

  private def runApp(
      hikariTransactor: Option[HikariTransactor[IO]],
      appConfig: AppConfig
  ): IO[ExitCode] =
    resources(hikariTransactor, appConfig).use {
      case (transactor, githubClient, stackoverflowClient, backend, linkPublisher) =>

        val clients = Map(
          "https://github.com/"        -> githubClient,
          "https://stackoverflow.com/" -> stackoverflowClient,
        )

        for {
          repositories <- Repositories.make(appConfig.db.inUse, Some(transactor))
          usecases = Usecases.make(repositories, clients.keys.toList)

          scheduler = QuartzScheduler(
            appConfig.scheduler,
            usecases.linkUsecase,
            linkPublisher,
            clients,
            logger
          )

          endpoints <- IO {
            List(
              ChatHandler.make(usecases.chatUsecase),
              LinkHandler.make(usecases.chatUsecase, usecases.linkUsecase, logger)
            ).flatMap(_.endpoints)
          }

          swaggerEndpoint = SwaggerInterpreter().fromServerEndpoints[IO](
            endpoints,
            "Scrapper API",
            "1.0.0"
          )

          port <- getPortSafe(appConfig.server.port)

          routes = Http4sServerInterpreter[IO]()
            .toRoutes(swaggerEndpoint ++ endpoints)

          _ <- IO.race(
            EmberServerBuilder
              .default[IO]
              .withHost(Host.fromString("0.0.0.0").get)
              .withPort(port)
              .withHttpApp(Router("/" -> routes).orNotFound)
              .build
              .evalTap(server =>
                logger.info(s"Server available at http://0.0.0.0:${server.address.getPort}")
              )
              .useForever,
            scheduler.runScheduler
          )
        } yield ExitCode.Success
    }