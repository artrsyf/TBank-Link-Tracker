package bot

import cats.effect.{ExitCode, IO, IOApp, Resource}
import cats.effect._
import cats.effect.kernel.Async
import cats.Parallel
import cats.syntax.all._
import cats.syntax.functor._
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.effect.Log.Stdout.instance
import fs2.kafka._

import com.comcast.ip4s.{Host, Port}
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Router
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.Logger

import sttp.client3.httpclient.cats.HttpClientCatsBackend
import sttp.tapir._
import sttp.tapir.json.tethysjson._
import sttp.tapir.server.http4s.Http4sServerInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter

import telegramium.bots.high._
import telegramium.bots.high.implicits._

import tethys._
import tethys.jackson._

import linktracker.config.AppConfig
import linkscrapper.dialog.repository.InMemory.InMemoryDialogRepository
import linktracker.link.delivery.http._
import linktracker.link.presenter.TelegramBotPresenter.TelegramBotPresenter
import linktracker.link.usecase.LinkUsecase
import linktracker.link.repository.Http.HttpLinkRepository
import linktracker.chat.repository.Http.HttpChatRepository
import linktracker.link.domain.dto.LinkUpdate
import linktracker.link.delivery.kafka.KafkaLinkUpdatesConsumer
import linktracker.link.cache.Redis.RedisLinkCache

object Main extends IOApp {
  import linktracker.link.domain.dto.LinkUpdate.given

  given logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  private def telegramBotApi(token: String) =
    s"https://api.telegram.org/bot${token}"

  private def getPortSafe(v: Int): IO[Port] =
    IO.delay(
      Port.fromInt(v).toRight(IllegalArgumentException("Invalid port value"))
    ).rethrow

  override def run(args: List[String]): IO[ExitCode] =
    for {
      appConfig <- AppConfig.load
      telegramConfig = appConfig.telegram
      httpConfig     = appConfig.transport.http
      cacheConfig    = appConfig.cache

      http4sBackend <- EmberClientBuilder.default[IO].build.use(IO.pure)
      sttpClient    <- HttpClientCatsBackend.resource[IO]().use(IO.pure)

      given Api[IO] = BotApi(http4sBackend, baseUrl = telegramBotApi(appConfig.telegram.botToken))

      redisCommandsResource = Redis[IO].utf8(cacheConfig.url)

      exitCode <- redisCommandsResource.use { redisCommands =>
        val linkCache = new RedisLinkCache[IO](redisCommands, logger)

        val dialogRepository = new InMemoryDialogRepository
        val linkRepository   = new HttpLinkRepository(httpConfig.scrapperServiceEndpoint, sttpClient, linkCache, logger)
        val chatRepository   = new HttpChatRepository(httpConfig.scrapperServiceEndpoint, sttpClient, logger)
        val bot = new TelegramBotPresenter[IO](
          linkRepository,
          chatRepository,
          dialogRepository,
          telegramConfig,
        )

        val linkUsecase = LinkUsecase.make(bot)

        for {
          port <- getPortSafe(appConfig.server.port)
          program <- appConfig.transport.`type` match {
            case "http" =>
              for {
                endpoints <- IO {
                  List(
                    LinkHandler(linkUsecase),
                  ).flatMap(_.endpoints)
                }

                swaggerEndpoint = SwaggerInterpreter().fromServerEndpoints[IO](
                  endpoints,
                  "Bot API",
                  "1.0.0"
                )

                routes = Http4sServerInterpreter[IO]()
                  .toRoutes(swaggerEndpoint ++ endpoints)

                server = EmberServerBuilder
                  .default[IO]
                  .withHost(Host.fromString("0.0.0.0").get)
                  .withPort(port)
                  .withHttpApp(Router("/" -> routes).orNotFound)
                  .build
                  .evalTap(server =>
                    logger.info(
                      s"Server available at http://0.0.0.0:${server.address.getPort}"
                    )
                  )

                _ <- List(server.useForever, bot.start()).parSequence
              } yield ()

            case "kafka" =>
              for {
                kafkaConsumer <- KafkaConsumer.resource(
                  ConsumerSettings[IO, String, Either[Throwable, List[LinkUpdate]]]
                    .withProperties(appConfig.transport.kafka.properties)
                ).allocated.map(_._1)

                kafkaProcess <- new KafkaLinkUpdatesConsumer[IO](
                  consumer = kafkaConsumer,
                  linkUsecase = linkUsecase,
                  logger = logger,
                  topic = appConfig.transport.kafka.topic
                ).process.start

                _ <- List(bot.start(), kafkaProcess.join).parSequence
              } yield ()

            case other =>
              IO.raiseError(new RuntimeException(s"Unsupported transport type: $other"))
          }
        } yield program
      }
    } yield ExitCode.Success
}
