name := """editorial-permissions-client"""

organization := "com.gu"

licenses += ("MIT", url("http://opensource.org/licenses/Apache-2.0"))

scalaVersion := "2.10.4"

crossScalaVersions := Seq("2.10.4", "2.11.4")

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-java-sdk" % "1.9.29",
  "com.typesafe.akka" %% "akka-actor" % "2.3.4",
  "com.typesafe.akka" %% "akka-agent" % "2.3.4",
  "com.typesafe.akka" %% "akka-slf4j" % "2.3.4",
  "net.liftweb" %% "lift-json" % "2.5",
  "org.scalatest" %% "scalatest" % "2.2.1" % "test"
)

com.typesafe.sbt.SbtGit.versionWithGit
