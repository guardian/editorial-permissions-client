import ReleaseTransformations._

name := """editorial-permissions-client"""

organization := "com.gu"

licenses += ("Apache-2.0", url("http://opensource.org/licenses/Apache-2.0"))

scalaVersion := "2.12.8"

crossScalaVersions := Seq("2.11.12")

scalacOptions ++= Seq("-feature", "-deprecation", "-language:higherKinds", "-Xfatal-warnings")

val akkaVersion = "2.5.19"
val awsSdkVersion = "1.11.280"

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-java-sdk-s3" % awsSdkVersion,
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "com.gu" %% "box" % "0.2.0",
  "net.liftweb" %% "lift-json" % "3.2.0",
  "org.mockito" % "mockito-all" % "1.10.19" % "test",
  "org.scalatest" %% "scalatest" % "3.0.4" % "test"
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
