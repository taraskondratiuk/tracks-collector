import sbt.Keys.name

lazy val commonSettings = Seq(
  Compile / PB.targets := Seq(
    scalapb.gen() -> (Compile / sourceManaged).value / "scalapb"
  ),
  scalaVersion := "2.13.8",
  scalacOptions ++= Seq(
    "-Xfatal-warnings",
    "-deprecation",
  ),
  libraryDependencies ++= Seq(
    "com.thesamet.scalapb"        %% "scalapb-runtime"              % scalapb.compiler.Version.scalapbVersion % "protobuf", //todo rm?
    "io.grpc"                     % "grpc-netty"                    % scalapb.compiler.Version.grpcJavaVersion,
    "com.thesamet.scalapb"        %% "scalapb-runtime-grpc"         % scalapb.compiler.Version.scalapbVersion,
    "org.scalatest"               %% "scalatest"                    % "3.2.12" % Test,
  )
)

lazy val circeVersion = "0.14.2"

lazy val utils = project
  .in(file("utils"))
  .settings(commonSettings ++ Seq(
    name := "utils",
    version := "0.1",
    libraryDependencies ++= Seq(
      "org.scalaj"                %% "scalaj-http"                  % "2.4.2",

      "io.circe"                  %% "circe-generic"                % circeVersion,
      "io.circe"                  %% "circe-parser"                 % circeVersion,
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
    ),
  ))
  .dependsOn(utils)
