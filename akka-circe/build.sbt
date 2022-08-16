import Dependencies._

name := "sangria-http-akka-circe"
organization := "org.sangria-graphql"

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "de.heikoseeberger" %% "akka-http-circe" % "1.37.0",
  "org.sangria-graphql" %% "sangria-circe" % SangriaVersion.sangriaCirce,
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test,
  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test,
  "io.circe" %% "circe-optics" % circeVersion % Test,
  "org.scalatest" %% "scalatest" % "3.2.9" % Test
)
