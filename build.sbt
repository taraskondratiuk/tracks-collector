import sbt.Keys.name

lazy val commonSettings = Seq(
  scalaVersion := "2.13.8",
  scalacOptions ++= Seq(
    "-Xfatal-warnings",
    "-deprecation",
  ),
  libraryDependencies ++= Seq(
    "org.scalatest"               %% "scalatest"                    % "3.2.14" % Test,
  )
)

lazy val circeVersion = "0.14.3"

lazy val utils = project
  .in(file("utils"))
  .settings(commonSettings ++ Seq(
    name := "utils",
    version := "0.1",
    libraryDependencies ++= Seq(
      "org.scalaj"                %% "scalaj-http"                  % "2.4.2",

      "io.circe"                  %% "circe-parser"                 % circeVersion,
      "io.circe"                  %% "circe-generic"                % circeVersion,
    )
  ))

lazy val collector = project
  .in(file("collector"))
  .settings(commonSettings ++ Seq(
    name := "collector",
    version := "0.1",
  ))
  .dependsOn(utils)

lazy val bot = project
  .in(file("bot"))
  .settings(commonSettings ++ Seq(
    name := "bot",
    version := "0.1",
    libraryDependencies ++= Seq(
      "org.telegram"              % "telegrambots"                  % "6.1.0",
      "org.telegram"              % "telegrambotsextensions"        % "6.1.0",

      "com.lihaoyi"               %% "cask"                         % "0.8.3",
    ),
  ))
  .dependsOn(utils)
