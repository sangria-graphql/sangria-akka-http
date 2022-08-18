import Dependencies._

name := "sangria-akka-http-core"
organization := "org.sangria-graphql"

libraryDependencies ++= Seq(
  "org.sangria-graphql" %% "sangria" % SangriaVersion.sangria,
  ("org.sangria-graphql" %% "sangria-slowlog" % SangriaVersion.sangriaSlowlog).cross(
    CrossVersion.for3Use2_13),
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "org.scalatest" %% "scalatest" % "3.2.13" % Test
)
