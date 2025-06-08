import sbt.*

object Dependencies {
  // cats
  val catsCore   = "org.typelevel" %% "cats-core"   % "2.13.0"
  val catsEffect = "org.typelevel" %% "cats-effect" % "3.5.7"

  // tapir
  val tapirVersion = "1.11.13"

  val tapirHttp4s     = "com.softwaremill.sttp.tapir" %% "tapir-http4s-server"     % tapirVersion
  val tapirSwagger    = "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle" % tapirVersion
  val tapirTethys     = "com.softwaremill.sttp.tapir" %% "tapir-json-tethys"       % tapirVersion
  val tapirSttpClient = "com.softwaremill.sttp.tapir" %% "tapir-sttp-client"       % tapirVersion

  // http4s
  val http4sVersion = "0.23.30"

  val http4sServer = "org.http4s" %% "http4s-ember-server" % http4sVersion
  val http4sDsl    = "org.http4s" %% "http4s-dsl"          % http4sVersion
  val http4sClient = "org.http4s" %% "http4s-ember-client" % http4sVersion

  // sttp
  val sttpVersion = "3.10.2"

  val sttpCore = "com.softwaremill.sttp.client3" %% "core" % sttpVersion
  val sttpCats = "com.softwaremill.sttp.client3" %% "cats" % sttpVersion

  // logback
  val logback = "ch.qos.logback" % "logback-classic" % "1.5.16"

  // tethys
  val tethysVersion = "0.29.3"

  val tethysCore       = "com.tethys-json" %% "tethys-core"       % tethysVersion
  val tethysJackson    = "com.tethys-json" %% "tethys-jackson213" % tethysVersion
  val tethysDerivation = "com.tethys-json" %% "tethys-derivation" % tethysVersion

  // pureconfig
  val pureConfigVersion = "0.17.8"

  val pureConfigCore    = "com.github.pureconfig" %% "pureconfig-core"           % pureConfigVersion
  val pureConfigGeneric = "com.github.pureconfig" %% "pureconfig-generic-scala3" % pureConfigVersion

  // telegramium
  val telegramiumVersion = "9.803.0"

  val telegramiumCore = "io.github.apimorphism" %% "telegramium-core" % telegramiumVersion
  val telegramiumHigh = "io.github.apimorphism" %% "telegramium-high" % telegramiumVersion

  // scheduler
  val quartzVersion = "1.0.4"

  val quartz = "com.itv" % "quartz4s-core_3" % quartzVersion

  // logging
  val log4catsVersion = "2.7.0"

  val log4catsSlf = "org.typelevel" %% "log4cats-slf4j"  % log4catsVersion
  val log4catsCore = "org.typelevel" %% "log4cats-core"   % log4catsVersion

  // testing
  val scalaTest = "org.scalatest" %% "scalatest" % "3.2.18" % Test
  val mockito = "org.scalatestplus" %% "mockito-4-11" % "3.2.18.0" % Test

  // RDMBS
  val doobieVersion = "1.0.0-RC8"
  val liquibaseVersion = "4.27.0"

  val rdbmsDependencies: List[ModuleID] = List(
    "org.liquibase"  % "liquibase-core"  % liquibaseVersion,
    "org.tpolecat"  %% "doobie-core"     % doobieVersion,
    "org.tpolecat"  %% "doobie-postgres" % doobieVersion,
    "org.tpolecat"  %% "doobie-hikari"   % doobieVersion,
    "org.tpolecat"  %% "doobie-h2"       % doobieVersion
  )

  // testcontainers
  val testcontainersScalaVersion = "0.43.0"

  val testContainers: List[ModuleID] = List(
    "com.dimafeng" %% "testcontainers-scala-scalatest" % testcontainersScalaVersion % Test,
    "com.dimafeng" %% "testcontainers-scala-postgresql" % testcontainersScalaVersion % Test,
    "org.postgresql" % "postgresql" % "42.5.1"
)

  val allDeps: Seq[ModuleID] = Seq(
    catsCore,
    catsEffect,
    tapirHttp4s,
    tapirSwagger,
    tapirTethys,
    tapirSttpClient,
    http4sServer,
    http4sDsl,
    http4sClient,
    sttpCore,
    sttpCats,
    logback,
    tethysCore,
    tethysJackson,
    tethysDerivation,
    pureConfigCore,
    pureConfigGeneric,
    telegramiumCore,
    telegramiumHigh,
    quartz,
    log4catsSlf,
    log4catsCore,
    scalaTest,
    mockito,
  ) ++ rdbmsDependencies ++ testContainers
}
