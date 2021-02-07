name := "sangria-akka-http"
organization := "org.sangria-graphql"

description := "Sangria Akka HTTP Support"
homepage := Some(url("https://sangria-graphql.github.io/"))
licenses := Seq(
  "Apache License, ASL Version 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"))

ThisBuild / crossScalaVersions := Seq("2.12.13", "2.13.4")
ThisBuild / scalaVersion := crossScalaVersions.value.last
ThisBuild / githubWorkflowPublishTargetBranches := List()
ThisBuild / githubWorkflowBuildPreamble ++= List(
  WorkflowStep.Sbt(List("scalafmtCheckAll"), name = Some("Check formatting"))
)

lazy val core = project in file("core")

lazy val circe = (project in file("circe"))
  .dependsOn(core % "test->test;compile->compile")

lazy val root = (project in file("."))
  .aggregate(core, circe)
  .settings(
    name := "sangria-akka-http",
    publishArtifact := false
  )
