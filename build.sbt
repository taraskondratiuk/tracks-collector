name := "tracks-collector"

version := "0.1"

scalaVersion := "2.13.8"

val circeVersion = "0.14.1"

scalacOptions ++= Seq(
  "-Xfatal-warnings",
  "-deprecation",
)

libraryDependencies ++= Seq(
  "org.scalaj"         %% "scalaj-http"         % "2.4.2",

  "io.circe"           %% "circe-generic"       % circeVersion,
  "io.circe"           %% "circe-parser"        % circeVersion,

  "org.scalatest"      %% "scalatest"           % "3.2.10" % Test,
)