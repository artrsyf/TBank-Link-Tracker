package migrator

import cats.effect.{ExitCode, IO, IOApp}
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.Logger

import java.sql.DriverManager
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor

import migrator.config.{AppConfig, DbConfig}

object Main extends IOApp:

  given logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  override def run(args: List[String]): IO[ExitCode] =
    for
      appConfig <- AppConfig.load
      _         <- logger.info("Migration starting")
      _         <- IO.println(appConfig.db)
      _         <- runLiquibaseMigration(appConfig.db, "changelog.xml")
      _         <- logger.info("Migration complete")
    yield ExitCode.Success

  private def runLiquibaseMigration(config: DbConfig, changelogPath: String): IO[Unit] =
    IO {
      Class.forName(config.driver)
      val conn = DriverManager.getConnection(config.url, config.user, config.password)
      try
        val database = DatabaseFactory.getInstance()
          .findCorrectDatabaseImplementation(new JdbcConnection(conn))
        val liquibase = new Liquibase(changelogPath, new ClassLoaderResourceAccessor(), database)
        liquibase.update("")
      finally conn.close()
    }
