import cats.effect._
import cats.syntax.functor._
import cats.effect.kernel.Async
import cats.Parallel
import cats.syntax.all._

import sttp.client3._
import sttp.client3.impl.cats._
import sttp.client3.httpclient.cats.HttpClientCatsBackend
import sttp.tapir._
import sttp.tapir.json.tethysjson
import sttp.model.StatusCode

import sttp.tapir.server.http4s.Http4sServerInterpreter
import org.http4s.ember.server.EmberServerBuilder
import com.comcast.ip4s.{Host, Port}
import org.http4s.server.Router

import tethys._
import tethys.jackson._
import tethys.derivation._

import telegramium.bots.high._
import telegramium.bots.high.implicits._
import telegramium.bots.{ChatIntId, Message}

import org.http4s.ember.client.EmberClientBuilder

import linktracker.config.AppConfig
import tethys.derivation.builder.WriterDescription.BuilderOperation.Add

import linktracker.link.delivery.http.*
import linktracker.link.presenter.TelegramBotPresenter.TelegramBotPresenter
import linktracker.link.usecase.LinkUsecase

object Http4sBot extends IOApp.Simple {
  override def run: IO[Unit] =
    AppConfig.load.flatMap { appConfig =>
      EmberClientBuilder.default[IO].build.use { backend =>
        given Api[IO] = BotApi(backend, baseUrl = s"https://api.telegram.org/bot${appConfig.telegram.botToken}")

        HttpClientCatsBackend.resource[IO]().use { backend => 
          val bot = new TelegramBotPresenter[IO](backend)
          val linkUsecase = LinkUsecase.make(bot)

          val linkHandler = new LinkHandler(linkUsecase)
          val routes = Http4sServerInterpreter[IO]().toRoutes(linkHandler.endpoints)
          val port = 8081
          val server = EmberServerBuilder
              .default[IO]
              .withHost(Host.fromString("localhost").get)
              .withPort(Port.fromInt(port).get)
              .withHttpApp(Router("/" -> routes).orNotFound)
              .build
              .evalTap(server =>
                      IO.println(
                        s"Server available at http://localhost:${server.address.getPort}"
                      )
                  )
              .useForever

          bot.start().both(server).void
        }
      }
    }
}

