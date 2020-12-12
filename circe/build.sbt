import Dependencies._

name := "sangria-akka-http-circe"

libraryDependencies ++= Seq(
  "org.sangria-graphql" %% "sangria-circe" % "1.3.1",
  "de.heikoseeberger" %% "akka-http-circe" % "1.35.0",

  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-parser" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-optics" % circeVersion,

  "org.scalatest" %% "scalatest" % "3.0.8" % Test
)
