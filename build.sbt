ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.3.5"

lazy val bot = (project in file("bot"))
  .settings(
    libraryDependencies ++= Dependencies.allDeps,
  )
  .enablePlugins(
    JavaAppPackaging,
    DockerPlugin,
  )
  .settings(
    Docker / packageName := "bot-api",
    dockerExposedPorts   := List(8081),
    dockerBaseImage      := "eclipse-temurin:21",
  )

lazy val scrapper = (project in file("scrapper"))
  .settings(
    libraryDependencies ++= Dependencies.allDeps,
  )
  .enablePlugins(
    JavaAppPackaging,
    DockerPlugin,
  )
  .settings(
    Docker / packageName := "scrapper-api",
    dockerExposedPorts   := List(8080),
    dockerBaseImage      := "eclipse-temurin:21",
  )

lazy val migrator = (project in file("migrator"))
  .settings(
    libraryDependencies ++= Dependencies.allDeps,
  )
  .settings(
    Compile / unmanagedResourceDirectories += baseDirectory.value / "src" / "main" / "migrations"
  )
  .enablePlugins(
    JavaAppPackaging,
    DockerPlugin,
  )
  .settings(
    Docker / packageName := "migrator-api",
    dockerExposedPorts   := List(8082),
    dockerBaseImage      := "eclipse-temurin:21",
  )
