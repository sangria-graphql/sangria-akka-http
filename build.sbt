
lazy val core = (project in file("core"))

lazy val circe = (project in file("circe"))
  .dependsOn(core % "test->test;compile->compile")

lazy val root = (project in file("."))
  .aggregate(core, circe)
  .settings(
    name := "sangria-akka-http"
  )
