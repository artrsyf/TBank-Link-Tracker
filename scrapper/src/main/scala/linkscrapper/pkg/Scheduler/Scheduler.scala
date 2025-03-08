package linkscrapper.pkg.Client.Scheduler

import cats.effect.IO
import cats.effect.std.Queue

import com.itv.scheduler._
import com.itv.scheduler.extruder.semiauto._
import org.quartz.{CronExpression, JobKey, TriggerKey}

import sttp.client3.httpclient.cats.HttpClientCatsBackend

import java.time.Instant

import linkscrapper.pkg.Client.GitHubClient.GitHubClient
import linkscrapper.config.SchedulerConfig

sealed trait ParentJob
case object CronJob extends ParentJob
case object CheckJob extends ParentJob

object ParentJob {
  implicit val jobCodec: JobCodec[ParentJob] = deriveJobCodec[ParentJob]
}

object QuartzScheduler {
  def runScheduler(schedulerConfig: SchedulerConfig): IO[Unit] = {
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
          _ <- scheduleCronJob(scheduler, schedulerConfig.cronExpression)
          _ <- scheduleSingleJob(scheduler)
          _ <- processQueue(jobMessageQueue)
        } yield ()
      }
    } yield ()
  }

  def scheduleCronJob(scheduler: QuartzTaskScheduler[IO, ParentJob], cronExpression: String): IO[Option[Instant]] =
    scheduler.scheduleJob(
      JobKey.jobKey("cron-job"),
      CronJob,
      TriggerKey.triggerKey("cron-job-trigger"),
      CronScheduledJob(new CronExpression(cronExpression))
    )

  def scheduleSingleJob(scheduler: QuartzTaskScheduler[IO, ParentJob]): IO[Option[Instant]] =
    scheduler.scheduleJob(
      JobKey.jobKey("single-check-job"),
      CheckJob,
      TriggerKey.triggerKey("scheduled-single-check-trigger"),
      JobScheduledAt(Instant.now.plusSeconds(2))
    )

  def processQueue(queue: Queue[IO, ParentJob]): IO[Unit] = {
    HttpClientCatsBackend.resource[IO]().use { backend =>
      queue.take.flatMap {
        case CronJob =>
          GitHubClient.getRepoUpdate(backend, "typelevel", "cats-effect").flatMap {
            case Right(repo) => IO.println(s"Repo last updated at: ${repo.updated_at}")
            case Left(error) => IO.println(s"Error fetching repo update: $error")
          }
        case CheckJob =>
          IO.println(s"Single job check")
      }.foreverM
    }
  }
}