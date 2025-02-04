ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.3.5"

val bot      = project.settings(libraryDependencies ++= Dependencies.allDeps)
val scrapper = project.settings(libraryDependencies ++= Dependencies.allDeps)
