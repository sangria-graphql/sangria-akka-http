ThisBuild / name := "sangria-akka-http"
ThisBuild / organization := "org.sangria-graphql"

ThisBuild / description := "Sangria Akka HTTP Support"
ThisBuild / homepage := Some(url("https://sangria-graphql.github.io/"))
ThisBuild / licenses := Seq(
  "Apache License, ASL Version 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"))

ThisBuild / crossScalaVersions := Seq("2.12.13", "2.13.4")
ThisBuild / scalaVersion := crossScalaVersions.value.last

ThisBuild / githubWorkflowPublishTargetBranches += RefPredicate.StartsWith(Ref.Tag("v"))
ThisBuild / githubWorkflowBuildPreamble ++= List(
  WorkflowStep.Sbt(List("scalafmtCheckAll"), name = Some("Check formatting"))
)
ThisBuild / githubWorkflowTargetTags ++= Seq("v*")

ThisBuild / githubWorkflowPublish := Seq(
  WorkflowStep.Sbt(
    List("ci-release"),
    env = Map(
      "PGP_PASSPHRASE" -> "${{ secrets.PGP_PASSPHRASE }}",
      "PGP_SECRET" -> "${{ secrets.PGP_SECRET }}",
      "SONATYPE_PASSWORD" -> "${{ secrets.SONATYPE_PASSWORD }}",
      "SONATYPE_USERNAME" -> "${{ secrets.SONATYPE_USERNAME }}"
    )
  )
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
