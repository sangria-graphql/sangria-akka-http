import Dependencies._

name := "sangria-akka-http-circe"

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "de.heikoseeberger" %% "akka-http-circe" % "1.35.0",
  "org.sangria-graphql" %% "sangria-circe" % "1.3.1" ,

  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion % Test,
  "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test,
  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test,
  "io.circe" %% "circe-optics" % circeVersion % Test
)
