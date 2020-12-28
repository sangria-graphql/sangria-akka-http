package sangria.http.akka

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.{ContentType, HttpEntity}
import io.circe.Json


object TestData {
  import GraphQLRequestUnmarshaller.`application/graphql`

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


  /* QueryString Parameters */
  private val UTF_8: String = StandardCharsets.UTF_8.toString
  val query: String = URLEncoder.encode(sampleQuery, UTF_8)
  val operationName: String = URLEncoder.encode(sampleOperationName, UTF_8)
  val variables: String = URLEncoder.encode(sampleVariables.toString, UTF_8)

  /* application/graphql entity */
  val queryAsGraphQL = HttpEntity(string = sampleQuery, contentType = `application/graphql`)


  /* Malformed Data */
  private val malformedQuery = "query Nope { fieldBad "
  val malformedQueryString: String = URLEncoder.encode(malformedQuery, UTF_8)
  val emptyBody: Json = Json.obj()
  val malformedJsonQuery = Json.obj(
    "query"-> Json.fromString(malformedQuery)
  )

  val badJson: HttpEntity.Strict =
    HttpEntity(string =
      s"""{
         |"query": "$sampleQuery",
         |"variables": i_am_not_json
         |}
         """.stripMargin, contentType = ContentTypes.`application/json`)

  val malformedGraphQLQuery: HttpEntity.Strict =
    HttpEntity(string = malformedQuery, contentType = `application/graphql`)

  val emptyGraphQLQuery: HttpEntity.Strict =
    HttpEntity(string = "", contentType = `application/graphql`)
}