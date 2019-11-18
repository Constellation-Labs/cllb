name := "cl-lb"

version := "0.1"

scalaVersion := "2.13.1"

resolvers += Resolver.sonatypeRepo("snapshots")

val catsVersion = "2.0.0"

libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-effect",
  "org.typelevel" %% "cats-core",
).map(_ % catsVersion)

libraryDependencies ++= Seq(
  "io.chrisdavenport" %% "log4cats-slf4j" % "1.0.1",
  "com.amazonaws" % "aws-java-sdk-elasticloadbalancingv2" % "1.11.661",
  "org.scalatest" %% "scalatest" % "3.0.8" % "test",
  "org.scalactic" %% "scalactic" % "3.0.8" % "test",
  "org.scalamock" %% "scalamock" % "4.4.0" % "test",
)

val circeVersion = "0.12.3"

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser"
).map(_ % circeVersion)

val http4sVersion = "0.21.0-M5"

libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-blaze-server",
  "org.http4s" %% "http4s-dsl",
  "org.http4s" %% "http4s-blaze-client",
  "org.http4s" %% "http4s-circe",
  "org.http4s" %% "http4s-dsl",
).map(_ % http4sVersion)

libraryDependencies += "org.slf4j" % "slf4j-jdk14" % "1.7.29"

fork in run := true

outputStrategy := Some(StdoutOutput)

// enablePlugins(JavaServerAppPackaging)
enablePlugins(JavaAppPackaging)

maintainer := "artur@evojam.com"