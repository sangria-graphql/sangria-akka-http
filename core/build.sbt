import Dependencies._

name := "sangria-http"
organization := "org.sangria-graphql"

libraryDependencies ++= Seq(
  "org.sangria-graphql" %% "sangria" % SangriaVersion.sangria,
)
