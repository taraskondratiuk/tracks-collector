lazy val commonSettings = Seq(
  scalaVersion := "2.13.8",
  scalacOptions ++= Seq(
    "-Xfatal-warnings",
    "-deprecation",
  ),
)

lazy val circeVersion = "0.14.5"
lazy val telegramApiVersion = "6.5.0"
lazy val catsEffectVersion = "3.4.8"

lazy val tracksCollector = project
  .in(file("tracks-collector"))
  .settings(commonSettings ++ Seq(
    name := "tracks-collector",
    version := "0.1",
    libraryDependencies ++= Seq(
      "org.telegram"               % "telegrambots"                  % telegramApiVersion,
      "org.telegram"               % "telegrambotsextensions"        % telegramApiVersion,

      "ch.qos.logback"             % "logback-classic"               % "1.4.6",
      "com.typesafe.scala-logging" %% "scala-logging"                % "3.9.5",

      "org.typelevel"              %% "cats-effect"                  % catsEffectVersion,

      "org.scalaj"                 %% "scalaj-http"                  % "2.4.2",

      "io.circe"                   %% "circe-parser"                 % circeVersion,
      "io.circe"                   %% "circe-generic"                % circeVersion,

      "org.mongodb.scala"          %% "mongo-scala-driver"           % "4.9.0",
      "org.scalatest"              %% "scalatest"                    % "3.2.15"               % Test,
    ),
  ))
