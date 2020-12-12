# Sangria akka-http Library

A reference, batteries included, [Sangria](https://github.com/sangria-graphql/sangria) [GraphQL](https://graphql.org) 
server implementation using [akka-http](https://github.com/akka/akka-http).

Various json libraries are supported:
- [circe](https://github.com/circe/circe)
- ???
- PRs adding the json library of your choice welcome

## Getting started

Add this to your dependencies:
```sbt
libraryDependencies += "org.sangria-graphql" %% "sangria-akka-http-core" % "2.0"
// And choose your desired json library support:
libraryDependencies += "org.sangria-graphql" %% "sangria-akka-http-circe" % "2.0"
```

```scala
include magic

val server = ???
```

For a full example, consult the [Sangria Akka Http example project](https://github.com/sangria-graphql/sangria-akka-http-example).
 
## Building
TODO: write

## Contributing
TODO: write
