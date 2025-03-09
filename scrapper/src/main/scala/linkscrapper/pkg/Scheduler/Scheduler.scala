package linkscrapper.pkg.Scheduler

import cats.effect.IO
import cats.syntax.traverse.*
import cats.effect.std.Queue
import cats.effect.kernel.Async

import com.itv.scheduler._
import com.itv.scheduler.extruder.semiauto._
import org.quartz.{CronExpression, JobKey, TriggerKey}

import sttp.client3.httpclient.cats.HttpClientCatsBackend

import java.time.Instant

import linkscrapper.pkg.Client.GitHubClient.GitHubClient
import linkscrapper.config.SchedulerConfig
import linkscrapper.Link.usecase.LinkUsecase

sealed trait ParentJob
case object CronJob extends ParentJob
case object CheckJob extends ParentJob

object ParentJob {
  implicit val jobCodec: JobCodec[ParentJob] = deriveJobCodec[ParentJob]
}

class QuartzScheduler(
  schedulerConfig: SchedulerConfig,
  linkUsecase: LinkUsecase[IO],
  githubClient: GitHubClient[IO],
) {
  private def fetchLinkUpdates: IO[Unit] =
    for {
      _ <- IO.delay(println("Scheduled link fetch"))
      links <- linkUsecase.getLinks
      updatedLinks <- links.traverse { link =>
        val parts = link.url.stripPrefix("https://github.com/").split("/")
        if (parts.length >= 2) {
          val owner = parts(0)
          val repo = parts(1)
          
          githubClient.getRepoUpdate(owner, repo).flatMap {
            case Right(repo) =>
              val githubUpdatedTime = Instant.parse(repo.updated_at)
              if (githubUpdatedTime.isAfter(link.updatedAt)) { 
                val updatedLink = link.copy(
                  updatedAt = githubUpdatedTime,
                )
                linkUsecase.updateLink(updatedLink).map(Some(_))
              } else IO.pure(None)

            case Left(_) => IO.pure(None)
          }
        } else IO.pure(None)
      }
      _ <- IO.delay(println(s"Updated links: ${updatedLinks.flatten}"))
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