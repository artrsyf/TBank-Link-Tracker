package linkscrapper.pkg.Scheduler

import java.time.Instant

import cats.effect.IO
import cats.effect.std.Queue
import cats.syntax.traverse.*

import com.itv.scheduler._
import com.itv.scheduler.extruder.semiauto._
import org.quartz.{CronExpression, JobKey, TriggerKey}

import sttp.client3._
import sttp.client3.httpclient.cats.HttpClientCatsBackend
import sttp.tapir.json.tethysjson
import tethys._
import tethys.jackson._

import linkscrapper.config.SchedulerConfig
import linkscrapper.Link.usecase.LinkUsecase
import linkscrapper.Link.domain.dto
import linkscrapper.pkg.Client.LinkClient

sealed trait ParentJob

object ParentJob {
  implicit val jobCodec: JobCodec[ParentJob] = deriveJobCodec[ParentJob]
}

case object CronJob extends ParentJob
case object CheckJob extends ParentJob

class QuartzScheduler(
  schedulerConfig: SchedulerConfig,
  linkUsecase: LinkUsecase[IO],
  clients: Map[String, LinkClient[IO]],
  backend: SttpBackend[IO, Any],
) {
  private def sendUpdatedLinks(linkUpdates: List[dto.LinkUpdate]): IO[Unit] = 
    val request = basicRequest
      .post(uri"${schedulerConfig.updatesHandlerUrl}")
      .body(linkUpdates.asJson)
      .contentType("application/json")

    for
      _ <- IO.delay(println(s"Sending updates: ${linkUpdates.asJson.toString}"))

      response <- backend.send(request).attempt
      _ <- response match {
        case Right(resp) if resp.code.isSuccess =>
          IO.delay(println(s"Successfully sent updates: ${resp.body}"))
        case Right(resp) =>
          IO.delay(println(s"Failed to send updates. Status: ${resp.code}, Body: ${resp.body}"))
        case Left(error) =>
          IO.delay(println(s"Error sending updates: ${error.getMessage}"))
      }
    yield ()

  private def fetchLinkUpdates: IO[Unit] =
    for {
      _ <- IO.delay(println("Scheduled link fetch"))

      links <- linkUsecase.getLinks
      _ <- IO.delay(println(s"Links: $links"))
      updatedLinks <- links.traverse { link =>
        clients.collectFirst {
          case (prefix, client) if link.url.startsWith(prefix) =>
            println(s"Choose client ${client.getClass().toString()} for url $link.url")

            client.getLastUpdate(link.url).flatMap {
              case Right(updatedTime) => 
                if (updatedTime.isAfter(link.updatedAt)) then
                  val updatedLink = link.copy(updatedAt = updatedTime)
                  
                  linkUsecase.updateLink(updatedLink).flatMap {
                    case Right(updatedLinkEntity) => 
                      IO.pure(Some(updatedLinkEntity))
                    case Left(errorResp) =>
                      IO.delay(println(s"Error updating link: ${errorResp.message}")).map(_ => None)
                  }
                else
                  IO.pure(None)
              case Left(error) => 
                IO.pure(None)
            }
        }.getOrElse(IO.pure(None))
      }
      groupedLinks = links.groupBy(_.url)
      linkUpdates = groupedLinks.flatMap { case (url, linkList) =>
        val tgChatIds = linkList.map(link => link.chatId).distinct

        if (tgChatIds.nonEmpty) {
          Some(dto.LinkUpdate(
            url = url,
            description = "Got update!",
            tgChatIds = tgChatIds,
          ))
        } else {
          None
        }
      }.toList
      _ = if linkUpdates.nonEmpty then sendUpdatedLinks(linkUpdates)
      _ <- IO.delay(println(s"Sent updated links to tg bot"))
    } yield ()

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
        IO.println(s"Single job check")
    }.foreverM
  }
}