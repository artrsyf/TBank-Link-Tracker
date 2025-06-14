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
import sttp.tapir.json.tethysjson
import tethys._
import tethys.jackson._
import fs2.Stream

import linkscrapper.config.SchedulerConfig
import linkscrapper.link.usecase.LinkUsecase
import linkscrapper.link.domain.dto
import linkscrapper.pkg.Client.LinkClient
import linkscrapper.pkg.Publisher.LinkPublisher

sealed trait ParentJob
object ParentJob {
  implicit val jobCodec: JobCodec[ParentJob] = deriveJobCodec[ParentJob]
}

case object CronJob  extends ParentJob
case object CheckJob extends ParentJob

class QuartzScheduler(
    schedulerConfig: SchedulerConfig,
    linkUsecase: LinkUsecase[IO],
    linkPublisher: LinkPublisher[IO],
    clients: Map[String, LinkClient[IO]],
    logger: Logger[IO],
) {
  private def fetchLinkUpdates: IO[Unit] = {
    linkUsecase.streamAllLinks.evalMap { link =>
      logger.info(s"Checking link | link=${link}")
      clients.collectFirst {
        case (prefix, client) if link.url.startsWith(prefix) =>
          client.getUpdates(link.url, link.updatedAt).flatMap {
            case Right(linkUpdatesRaw) =>
              val linkUpdates = linkUpdatesRaw.filter(_.updatedAt.isAfter(link.updatedAt))

              if (linkUpdates.isEmpty) {
                logger.info("Nothing to update") *> IO.pure(None)
              } else {
                logger.info(s"Found link updates | count=${linkUpdates.length}")

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
          linkPublisher.publishLinks(linkUpdates) *> logger.info(
            s"Sending updated links to bot-service | count=${linkUpdates.length}"
          )
        else
          IO.unit
      }
  }

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
        fetchLinkUpdates *> logger.info(s"Executing link fetch job")
      case CheckJob =>
        logger.info(s"Executing smoke job")
    }.foreverM
  }
}
