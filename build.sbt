name := """editorial-permissions-client"""

organization := "com.gu"

licenses += ("Apache-2.0", url("http://opensource.org/licenses/Apache-2.0"))

scalaVersion := "2.11.7"

crossScalaVersions := Seq("2.10.4", "2.11.4")

scalacOptions ++= Seq("-feature", "-deprecation", "-language:higherKinds", "-Xfatal-warnings")

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-java-sdk-core" % "1.10.37",
  "com.amazonaws" % "aws-java-sdk-s3" % "1.10.37",
  "com.typesafe.akka" %% "akka-actor" % "2.4.1",
  "com.typesafe.akka" %% "akka-agent" % "2.4.1",
  "com.typesafe.akka" %% "akka-slf4j" % "2.4.1",
  "net.liftweb" %% "lift-json" % "2.6.2",
  "org.mockito" % "mockito-all" % "1.10.19" % "test",
  "org.scalatest" %% "scalatest" % "2.2.5" % "test"
)

com.typesafe.sbt.SbtGit.versionWithGit
