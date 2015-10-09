name := """editorial-permissions-client"""

organization := "com.gu"

licenses += ("MIT", url("http://opensource.org/licenses/Apache-2.0"))

scalaVersion := "2.10.4"

crossScalaVersions := Seq("2.10.4", "2.11.4")

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "2.2.1" % "test"
)

com.typesafe.sbt.SbtGit.versionWithGit
