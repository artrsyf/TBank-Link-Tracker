import cats.effect._
import cats.effect.kernel.Async
import cats.Parallel
import cats.syntax.all._
import cats.syntax.functor._

import com.comcast.ip4s.{Host, Port}
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Router

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
import linktracker.link.delivery.http._
import linktracker.link.presenter.TelegramBotPresenter.TelegramBotPresenter
import linktracker.link.usecase.LinkUsecase

object Main extends IOApp {
  def telegramBotApi(token: String) =
    s"https://api.telegram.org/bot${token}"
  override def run(args: List[String]): IO[ExitCode] =
    for {
      appConfig <- AppConfig.load

      http4sBackend <- EmberClientBuilder.default[IO].build.use(IO.pure)
      sttpClient    <- HttpClientCatsBackend.resource[IO]().use(IO.pure)

      given Api[IO] = BotApi(http4sBackend, baseUrl = telegramBotApi(appConfig.telegram.botToken))

      bot         = new TelegramBotPresenter[IO](sttpClient, appConfig.telegram)
      linkUsecase = LinkUsecase.make(bot)

      endpoints <-
        IO {
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

      port <- getPortSafe(appConfig.server.port)

      server = EmberServerBuilder
        .default[IO]
        .withHost(Host.fromString("0.0.0.0").get) // docker fix?
        .withPort(port)
        .withHttpApp(Router("/" -> routes).orNotFound)
        .build
        .evalTap(server =>
          IO.println(
            s"Server available at http://localhost:${server.address.getPort}"
          )
        )
        .useForever
      _ <- bot.start().both(server)
    } yield ExitCode.Success

  private def getPortSafe(v: Int): IO[Port] =
    IO.delay(
      Port.fromInt(v).toRight(IllegalArgumentException("Invalid port value"))
    ).rethrow
}
