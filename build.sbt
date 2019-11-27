import com.typesafe.sbt.packager.docker._

name := "cl-lb"

version := "0.1.1"

scalaVersion := "2.13.1"

resolvers += Resolver.sonatypeRepo("snapshots")

val catsVersion = "2.0.0"
val circeVersion = "0.12.3"
val http4sVersion = "0.21.0-M5"

libraryDependencies ++= Seq(
  "io.chrisdavenport" %% "log4cats-slf4j" % "1.0.1",
  "com.amazonaws" % "aws-java-sdk-elasticloadbalancingv2" % "1.11.661",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.github.cb372" %% "scalacache-guava" % "0.28.0",
  "com.github.cb372" %% "scalacache-cats-effect" % "0.28.0",
  "com.github.pureconfig" %% "pureconfig" % "0.12.1"
)

libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-effect",
  "org.typelevel" %% "cats-core",
).map(_ % catsVersion)

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser"
).map(_ % circeVersion)

libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-blaze-server",
  "org.http4s" %% "http4s-dsl",
  "org.http4s" %% "http4s-blaze-client",
  "org.http4s" %% "http4s-circe",
  "org.http4s" %% "http4s-dsl",
).map(_ % http4sVersion)

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "3.0.8",
  "org.scalactic" %% "scalactic" % "3.0.8",
  "org.scalamock" %% "scalamock" % "4.4.0",
).map(_ % "it,test")

//enablePlugins(GatlingPlugin)
//scalacOptions := Seq(
//  "-encoding", "UTF-8", "-target:jvm-1.8", "-deprecation",
//  "-feature", "-unchecked", "-language:implicitConversions", "-language:postfixOps")
//
//libraryDependencies += "io.gatling.highcharts" % "gatling-charts-highcharts" % "3.3.1" % "test,it"
//libraryDependencies += "io.gatling"            % "gatling-test-framework"    % "3.3.1" % "test,it"

configs(IntegrationTest)

Defaults.itSettings

fork in run := true

outputStrategy := Some(StdoutOutput)

enablePlugins(JavaAppPackaging)

maintainer := "artur@evojam.com"

enablePlugins(DockerPlugin)

packageName in Docker := "abankowski/cluster-loadbalancer"

dockerBaseImage := "openjdk:12-alpine"

dockerExposedPorts := Seq(9000)

dockerCommands ++= Seq(
          Cmd("USER", "root"),
          Cmd("RUN", "apk add --update python python-dev py-pip build-base && " +
                      "pip install awscli --upgrade" +
                      "&& apk --purge -v del py-pip  && rm -rf /var/cache/apk/*"),
          ) ++ dockerUsername.value.map(Cmd("USER", _)).toSeq

javaOptions in Universal ++= Seq("-Dconfig.file=/tmp/application.conf")

enablePlugins(AshScriptPlugin)
