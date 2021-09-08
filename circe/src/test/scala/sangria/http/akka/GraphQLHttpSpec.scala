package sangria.http.akka

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.{Assertion, Suite}
import org.scalatest.flatspec.AnyFlatSpec
import io.circe.Json
import sangria.parser.SyntaxError
import sangria.http.akka.SangriaAkkaHttp._
import sangria.http.akka.circe.CirceHttpSupport

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

trait GraphQLHttpSpec extends AnyFlatSpec with ScalatestRouteTest with CirceHttpSupport {
  import TestData._

  val queryOnly: String = s"""
                             |[DOCUMENT]: "$sampleQuery"
                             |[OPERATION_NAME]: None
                             |[VARIABLES]: {}""".stripMargin

  val namedQuery: String = s"""
                              |[DOCUMENT]: "$sampleQuery"
                              |[OPERATION_NAME]: Some($sampleOperationName)
                              |[VARIABLES]: {}""".stripMargin

  val queryWithVariables: String = s"""
                                      |[DOCUMENT]: "$sampleQuery"
                                      |[OPERATION_NAME]: None
                                      |[VARIABLES]: ${sampleVariables.noSpacesSortKeys}""".stripMargin

  val namedQueryWithVariables: String = s"""
                                           |[DOCUMENT]: "$sampleQuery"
                                           |[OPERATION_NAME]: Some($sampleOperationName)
                                           |[VARIABLES]: ${sampleVariables.noSpacesSortKeys}""".stripMargin

  val queryOnlyCheck: RouteTestResult => Assertion = check {
    val resp = responseAs[String]
    assert(
      responseAs[String] == queryOnly,
      s"""
         |QueryOnly expects ONLY a GraphQL `query`, please confirm
         |that you have not passed an `operationName` or `variables` and retry.
         |
         |Expected output was:
         |$queryOnly
         |Received:
         |$resp""".stripMargin
    )
  }

  val namedQueryCheck: RouteTestResult => Assertion = check {
    val resp = responseAs[String]
    assert(
      responseAs[String] == namedQuery,
      s"""
         |NamedQuery expects a GraphQL `query`, AND `operationName`,
         |please confirm that you have not passed `variables` and retry.
         |
         |Expected output was:
         |$namedQuery
         |Received:
         |$resp""".stripMargin
    )
  }

  val queryWithVariablesCheck: RouteTestResult => Assertion = check {
    val resp = responseAs[String]
    assert(
      responseAs[String] == queryWithVariables,
      s"""
         |QueryWithVariables expects a GraphQL `query`, AND `variables`,
         |please confirm that you have not passed `operationName` and retry.
         |
         |Expected output was:
         |$queryWithVariables
         |Received:
         |$resp""".stripMargin
    )
  }

  val namedQueryWithVariablesCheck: RouteTestResult => Assertion = check {
    val resp = responseAs[String]
    assert(
      responseAs[String] == namedQueryWithVariables,
      s"""
         |NamedQueryWithVariables expects a GraphQL `query`, `variables`, AND `operationName`.
         |Confirm that you have passed all required parameters and retry.
         |
         |Expected output was:
         |$namedQueryWithVariables
         |Received:
         |$resp""".stripMargin
    )
  }
}

/** Provides a route for tests in this project. */
trait GraphQLHttpSpecRoute extends CirceHttpSupport { this: Suite =>
  implicit def executor: ExecutionContext

  /** The path, from the root, at which the service home resides. */
  val graphQLPath = "graphql"

  /** A route for testing the service.
    *
    * The route simply completes with a string containing the arguments that were passed in:
    * the query document, operation name, and variables.
    * It parses the GraphQL request document, but doesn't execute it against a GraphQL server.
    * This is sufficient for testing the Akka HTTP directives that this library provides.
    */
  val route: Route = {
    path(graphQLPath) {
      prepareGraphQLRequest {
        /*FIXME The `source.get` below depends on `prepareGraphQLRequest` having quietly, internally, called
         * `QueryParser.parse()` using the default `ParserConfig`, which uses the default `SourceMapper` function,
         * which uses the `DefaultSourceMapper`, which creates a non-empty `source` member in the `SourceMapper`,
         * which gets surfaced here through the `Document`.  Whew!
         * Obviously this sort of magic shouldn't be buried inside `prepareGraphQLRequest`.  Part of this PR's goal
         * is to surface that magic.
         */
        case Success(req) => complete(s"""
               |[DOCUMENT]: ${Json.fromString(req.query.source.get).noSpacesSortKeys}
               |[OPERATION_NAME]: ${req.operationName}
               |[VARIABLES]: ${req.variables.noSpacesSortKeys}""".stripMargin)
        case Failure(err) =>
          err match {
            case err: SyntaxError =>
              val e = formatError(err)
              complete(UnprocessableEntity, GraphQLErrorResponse(e :: Nil))
            case err: MalformedRequest =>
              val e = formatError(err)
              complete(BadRequest, GraphQLErrorResponse(e :: Nil))
          }
      }
    } ~
      (get & pathEndOrSingleSlash) {
        redirect(s"/$graphQLPath", PermanentRedirect)
      }
  }
}
