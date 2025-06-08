package linkscrapper.pkg.Scheduler

import java.time.Instant

import cats.effect.IO
import cats.effect.std.Queue
import cats.implicits.catsSyntaxApplicativeByName
import cats.syntax.traverse.*

import com.itv.scheduler._
import com.itv.scheduler.extruder.semiauto._
import org.quartz.{CronExpression, JobKey, TriggerKey}
import org.typelevel.log4cats.Logger

import sttp.client3._
import sttp.client3.httpclient.cats.HttpClientCatsBackend
import sttp.tapir.json.tethysjson
import tethys._
import tethys.jackson._
import fs2.Stream

import linkscrapper.config.SchedulerConfig
import linkscrapper.link.usecase.LinkUsecase
import linkscrapper.link.domain.dto
import linkscrapper.pkg.Client.LinkClient

sealed trait ParentJob
object ParentJob {
  implicit val jobCodec: JobCodec[ParentJob] = deriveJobCodec[ParentJob]
}

case object CronJob  extends ParentJob
case object CheckJob extends ParentJob

class QuartzScheduler(
    schedulerConfig: SchedulerConfig,
    linkUsecase: LinkUsecase[IO],
    clients: Map[String, LinkClient[IO]],
    backend: SttpBackend[IO, Any],
    logger: Logger[IO],
) {
  private def sendUpdatedLinks(linkUpdates: List[dto.LinkUpdate]): IO[Unit] =
    val request = basicRequest
      .post(uri"${schedulerConfig.updatesHandlerUrl}")
      .body(linkUpdates.asJson)
      .contentType("application/json")

    for
      _ <- logger.info(s"Sending updates to bot-service | linkCount=${linkUpdates.length}")

      response <- backend.send(request).attempt
      _ <- response match {
        case Right(resp) if resp.code.isSuccess =>
          logger.info("Successfully sent updates to bot-service")
        case Right(resp) =>
          logger.warn(
            s"Unexpected response code from bot-service | linkCount=${linkUpdates.length} " +
              s"| status=${resp.code} | Body=${resp.body}"
          )
        case Left(error) =>
          logger.error(
            s"Error sending updates to bot-service | linkCount=${linkUpdates.length} | error=${error.getMessage}"
          )
      }
    yield ()

  private def fetchLinkUpdates: IO[Unit] = {
    linkUsecase.streamAllLinks.evalMap { link =>
      clients.collectFirst {
        case (prefix, client) if link.url.startsWith(prefix) =>
          client.getUpdates(link.url, link.updatedAt).flatMap {
            case Right(linkUpdatesRaw) =>
              val linkUpdates = linkUpdatesRaw.filter(_.updatedAt.isAfter(link.updatedAt))

              if (linkUpdates.isEmpty) IO.pure(None)
              else {
                val updatedLink = link.copy(updatedAt = Instant.now())
                linkUsecase.updateLink(updatedLink).flatMap {
                  case Right(updatedLinkEntity) =>
                    IO.pure(Some((updatedLinkEntity, linkUpdates)))
                  case Left(errorResp) =>
                    logger.warn(s"Failed to update link | error=${errorResp.message}").as(None)
                }
              }

            case Left(_) =>
              IO.pure(None)
          }
      }.getOrElse(IO.pure(None))
    }
      .unNone
      .evalMap { case (updatedLink, updates) =>
        for {
          userLinks <- linkUsecase.getUserLinksByLinkUrl(updatedLink.url)
          tgChatIds   = userLinks.map(_.chatId)
          description = updates.map(_.description).mkString("\n\n---\n\n")
        } yield {
          if (tgChatIds.nonEmpty)
            Some(dto.LinkUpdate(updatedLink.url, description, tgChatIds))
          else None
        }
      }
      .unNone
      .compile
      .toList
      .flatMap { linkUpdates =>
        if (linkUpdates.nonEmpty)
          sendUpdatedLinks(linkUpdates) *> logger.info(
            s"Sending updated links to bot-service | count=${linkUpdates.length}"
          )
        else
          IO.unit
      }
  }

  // private def fetchLinkUpdates: IO[Unit] =
  //   for {
  //     _ <- logger.info("Fetching link updates")

  //     links <- linkUsecase.getLinks
  //     updatedLinks <- links.traverse { link =>
  //       clients.collectFirst {
  //         case (prefix, client) if link.url.startsWith(prefix) =>
  //           client.getUpdates(link.url, link.updatedAt).flatMap {
  //             case Right(linkUpdatesRaw) =>
  //               val linkUpdates = linkUpdatesRaw.filter(_.updatedAt.isAfter(link.updatedAt))

  //               if (linkUpdates.isEmpty) IO.pure(None)
  //               else {
  //                 val updatedLink = link.copy(updatedAt = Instant.now())
  //                 linkUsecase.updateLink(updatedLink).flatMap {
  //                   case Right(updatedLinkEntity) =>
  //                     IO.pure(Some((updatedLinkEntity, linkUpdates)))
  //                   case Left(errorResp) =>
  //                     logger.warn(s"Failed to update link | error=${errorResp.message}").as(None)
  //                 }
  //               }

  //             case Left(_) => IO.pure(None)
  //           }
  //       }.getOrElse(IO.pure(None))
  //     }

  //     linkUpdates <- updatedLinks.flatten.traverse { case (updatedLink, updates) =>
  //       for {
  //         userLinks <- linkUsecase.getUserLinksByLinkUrl(updatedLink.url)
  //         tgChatIds = userLinks.map(_.chatId)
  //         description = updates.map(_.description).mkString("\n\n---\n\n")
  //       } yield {
  //         if (tgChatIds.nonEmpty)
  //           Some(dto.LinkUpdate(
  //             url = updatedLink.url,
  //             description = description,
  //             tgChatIds = tgChatIds
  //           ))
  //         else None
  //       }
  //     }.map(_.flatten)

  //     _ <- sendUpdatedLinks(linkUpdates).whenA(linkUpdates.nonEmpty)
  //     _ <- logger.info(s"Sending updated links to bot-service | count=${linkUpdates.length}")
  //       .whenA(linkUpdates.nonEmpty)
  //   } yield ()

  def runScheduler: IO[Unit] = {
    val quartzProperties = new java.util.Properties()
    quartzProperties.setProperty("org.quartz.threadPool.threadCount", schedulerConfig.threadNumber.toString)

    for {
      jobMessageQueue <- Queue.unbounded[IO, ParentJob]
      autoAckJobFactory = MessageQueueJobFactory.autoAcking(jobMessageQueue)

      schedulerResource = autoAckJobFactory.flatMap { jobFactory =>
        QuartzTaskScheduler[IO, ParentJob](QuartzProperties(quartzProperties), jobFactory)
      }
      _ <- schedulerResource.use { scheduler =>
        for {
          _ <- scheduleCronJob(scheduler)
          _ <- scheduleSingleJob(scheduler)
          _ <- processQueue(jobMessageQueue)
        } yield ()
      }
    } yield ()
  }

  def scheduleCronJob(scheduler: QuartzTaskScheduler[IO, ParentJob]): IO[Option[Instant]] =
    scheduler.scheduleJob(
      JobKey.jobKey("cron-job"),
      CronJob,
      TriggerKey.triggerKey("cron-job-trigger"),
      CronScheduledJob(new CronExpression(schedulerConfig.cronExpression))
    )

  def scheduleSingleJob(scheduler: QuartzTaskScheduler[IO, ParentJob]): IO[Option[Instant]] =
    scheduler.scheduleJob(
      JobKey.jobKey("single-check-job"),
      CheckJob,
      TriggerKey.triggerKey("scheduled-single-check-trigger"),
      JobScheduledAt(Instant.now.plusSeconds(2))
    )

  def processQueue(queue: Queue[IO, ParentJob]): IO[Unit] = {
    queue.take.flatMap {
      case CronJob =>
        fetchLinkUpdates
      case CheckJob =>
        logger.info(s"Single job check")
    }.foreverM
  }
}
