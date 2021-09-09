package sangria.http.akka

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.HttpEntity
import io.circe.Json

object TestData {
  import GraphQLRequestUnmarshaller.`application/graphql`

  /** A GraphQL query request having a single, named operation. */
  val sampleQuery = "query TestQuery { fieldName }"
  val sampleOperationName = "TestQuery"
  val sampleVariables: Json = Json.obj(
    "variableA" -> Json.fromString("Hello"),
    "variableB" -> Json.fromInt(1),
    "variableC" -> Json.obj(
      "variableC.a" -> Json.fromString("World")
    )
  )

  /* JSON Payloads */
  val bodyQueryOnly: Json = Json.obj(
    "query" -> Json.fromString(sampleQuery)
  )
  val bodyNamedQuery: Json = Json.obj(
    "query" -> Json.fromString(sampleQuery),
    "operationName" -> Json.fromString(sampleOperationName)
  )
  val bodyWithVariables: Json = Json.obj(
    "query" -> Json.fromString(sampleQuery),
    "variables" -> sampleVariables
  )
  val bodyNamedQueryWithVariables: Json = Json.obj(
    "query" -> Json.fromString(sampleQuery),
    "operationName" -> Json.fromString(sampleOperationName),
    "variables" -> sampleVariables
  )

  /** JSON GraphQL request object having `operationName` and `variables` but no `query`. */
  val bodyWithNameAndVariables: Json = Json.obj(
    "operationName" -> Json.fromString(sampleOperationName),
    "variables" -> sampleVariables
  )

  /* QueryString Parameters */
  private val UTF_8: String = StandardCharsets.UTF_8.toString
  val query: String = URLEncoder.encode(sampleQuery, UTF_8)
  val operationName: String = URLEncoder.encode(sampleOperationName, UTF_8)
  val variables: String = URLEncoder.encode(sampleVariables.toString, UTF_8)

  /* application/graphql entity */
  val queryAsGraphQL: HttpEntity.Strict = HttpEntity(string = sampleQuery, contentType = `application/graphql`)

  /** An invalid GraphQL request. */
  private[this] final val malformedQuery = "query Nope { fieldBad "

  /** An URL-encoded, [[malformedQuery invalid GraphQL request]] (for use in a URI). */
  val malformedQueryString: String = URLEncoder.encode(malformedQuery, UTF_8)

  /** An empty JSON object. */
  val emptyBody: Json = Json.obj()
  val malformedJsonQuery: Json = Json.obj(
    "query" -> Json.fromString(malformedQuery)
  )

  /** HTTP entity having JSON content type but containing invalid JSON. */
  val badJson: HttpEntity.Strict =
    HttpEntity(
      string = s"""{
         |"query": "$sampleQuery",
         |"variables": i_am_not_json
         |}
         """.stripMargin,
      contentType = ContentTypes.`application/json`
    )

  val malformedGraphQLQuery: HttpEntity.Strict =
    HttpEntity(string = malformedQuery, contentType = `application/graphql`)

  /** HTTP entity having GraphQL content type but empty content. */
  val emptyGraphQLQuery: HttpEntity.Strict =
    HttpEntity(string = "", contentType = `application/graphql`)
}
