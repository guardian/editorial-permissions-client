import ReleaseTransformations._

name := """editorial-permissions-client"""

organization := "com.gu"

licenses += ("Apache-2.0", url("http://opensource.org/licenses/Apache-2.0"))

scalaVersion := "2.11.7"

crossScalaVersions := Seq("2.10.4", "2.11.4")

scalacOptions ++= Seq("-feature", "-deprecation", "-language:higherKinds", "-Xfatal-warnings")

val akkaVersion = "2.4.1"
val awsSdkVersion = "1.10.37"

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-java-sdk-s3" % awsSdkVersion,
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-agent" % akkaVersion,
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "net.liftweb" %% "lift-json" % "2.6.2",
  "org.mockito" % "mockito-all" % "1.10.19" % "test",
  "org.scalatest" %% "scalatest" % "2.2.5" % "test"
)

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  ReleaseStep(action = Command.process("publishSigned", _)),
  setNextVersion,
  commitNextVersion,
  ReleaseStep(action = Command.process("sonatypeReleaseAll", _)),
  pushChanges
)