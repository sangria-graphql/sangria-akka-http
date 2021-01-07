package sangria.http.akka
import akka.http.scaladsl.model.StatusCodes.{BadRequest, UnprocessableEntity}
import org.scalatest.{FlatSpec, Matchers}
import io.circe.Json

class CirceGraphQLTest
    extends FlatSpec
    with Matchers
    with GraphQLHttpSpec
    with GraphQLHttpSpecRoute {
  import TestData._
  val path = s"/$graphQLPath"

  it should "handle an HTTP GET Request" in {
    Get(s"$path?query=$query") ~> route ~> queryOnlyCheck
    Get(s"$path?query=$query&operationName=$operationName") ~> route ~> namedQueryCheck
    Get(s"$path?query=$query&variables=$variables") ~> route ~> queryWithVariablesCheck
    Get(s"$path?query=$query&operationName=$operationName&variables=$variables") ~> route ~> namedQueryWithVariablesCheck
  }

  it should "handle an HTTP POST Request (application/json)" in {
    Post(path, bodyQueryOnly) ~> route ~> queryOnlyCheck
    Post(path, bodyNamedQuery) ~> route ~> namedQueryCheck
    Post(path, bodyWithVariables) ~> route ~> queryWithVariablesCheck
    Post(path, bodyNamedQueryWithVariables) ~> route ~> namedQueryWithVariablesCheck
  }

  it should "handle an HTTP POST Request (application/graphql)" in {
    Post(path, queryAsGraphQL) ~> route ~> queryOnlyCheck
    Post(s"$path?operationName=$operationName", queryAsGraphQL) ~> route ~> namedQueryCheck
    Post(s"$path?variables=$variables", queryAsGraphQL) ~> route ~> queryWithVariablesCheck
    Post(
      s"$path?operationName=$operationName&variables=$variables",
      queryAsGraphQL) ~> route ~> namedQueryWithVariablesCheck
  }

  // TODO: Make this even better
  private val syntaxErrorCheck = check {
    val resp = responseAs[Json]
    val ind = resp.noSpacesSortKeys.indexOf("Syntax error")
    assert(ind >= 0)
    assert(response.status == UnprocessableEntity)
  }

  it should "Indicate a bad request, and Syntax Error when provided a malformed query" in {
    Get(s"$path?query=$malformedQueryString") ~> route ~> syntaxErrorCheck
    Post(path, malformedJsonQuery) ~> route ~> syntaxErrorCheck
    Post(path, malformedGraphQLQuery) ~> route ~> syntaxErrorCheck
  }

  // TODO: Make this even better
  private val missingQueryCheck = check {
    val resp = responseAs[Json]
    val ind = resp.noSpacesSortKeys.indexOf("Could not extract `query`")
    assert(ind >= 0)
    assert(response.status == BadRequest)
  }

  it should "Indicate a bad request, and a Could not extract `query` message when missing query" in {
    Get(s"$path?operationName=Nope") ~> route ~> missingQueryCheck
    Post(path, emptyBody) ~> route ~> missingQueryCheck
    Post(path, emptyGraphQLQuery) ~> route ~> missingQueryCheck
  }

  // TODO: Make this even better
  private val badVariablesCheck = check {
    assert(response.status == UnprocessableEntity)
  }
  it should "Indicate a bad request, and a yell about variables if provided invalid variables" in {
    Get(s"$path?query=$query&variables=i_am_not_json") ~> route ~> badVariablesCheck
    Post(path, badJson) ~> route ~> badVariablesCheck
    Post(
      s"$path?query=$query&variables=i_am_not_json",
      queryAsGraphQL) ~> route ~> badVariablesCheck
  }
}
