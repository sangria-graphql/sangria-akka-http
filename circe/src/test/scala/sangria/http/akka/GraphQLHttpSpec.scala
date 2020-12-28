package sangria.http.akka


import akka.http.scaladsl.model.StatusCodes.{BadRequest, PermanentRedirect}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.{Assertion, FlatSpec, Suite}
import sangria.http.akka.SangriaAkkaHttp._
import sangria.http.akka.circe.CirceHttpSupport._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.Json

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

trait GraphQLHttpSpec extends FlatSpec with ScalatestRouteTest {
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
    assert(responseAs[String] == queryOnly,
      s"""
         |QueryOnly expects ONLY a GraphQL `query`, please confirm
         |that you have not passed an `operationName` or `variables` and retry.
         |
         |Expected output was:
         |$queryOnly
         |Received:
         |$resp""".stripMargin)
  }

  val namedQueryCheck: RouteTestResult => Assertion = check {
    val resp = responseAs[String]
    assert(responseAs[String] == namedQuery,
      s"""
         |NamedQuery expects a GraphQL `query`, AND `operationName`,
         |please confirm that you have not passed `variables` and retry.
         |
         |Expected output was:
         |$namedQuery
         |Received:
         |$resp""".stripMargin)
  }

  val queryWithVariablesCheck: RouteTestResult => Assertion = check {
    val resp = responseAs[String]
    assert(responseAs[String] == queryWithVariables,
      s"""
         |QueryWithVariables expects a GraphQL `query`, AND `variables`,
         |please confirm that you have not passed `operationName` and retry.
         |
         |Expected output was:
         |$queryWithVariables
         |Received:
         |$resp""".stripMargin)
  }

  val namedQueryWithVariablesCheck: RouteTestResult => Assertion = check {
    val resp = responseAs[String]
    assert(responseAs[String] == namedQueryWithVariables,
      s"""
         |NamedQueryWithVariables expects a GraphQL `query`, `variables`, AND `operationName`.
         |Confirm that you have passed all required parameters and retry.
         |
         |Expected output was:
         |$namedQueryWithVariables
         |Received:
         |$resp""".stripMargin)
  }
}

trait GraphQLHttpSpecRoute { this: Suite =>
  implicit def executor: ExecutionContext

  val graphQLPath = "graphql"

  val route: Route = {
    path(graphQLPath) {
        prepareGraphQLRequest[Json] {
          // Yes, there is a `.get` here, and if this throws
          // you have a far larger issue than this library.
          // thank you for coming to my TED Talk.
          case Success(req) => complete(
            s"""
               |[DOCUMENT]: ${Json.fromString(req.query.source.get).noSpacesSortKeys}
               |[OPERATION_NAME]: ${req.operationName}
               |[VARIABLES]: ${req.variables.noSpacesSortKeys}""".stripMargin)
          case Failure(preparationError) => complete(BadRequest, formatError(preparationError))
        }
    } ~
      (get & pathEndOrSingleSlash) {
        redirect(s"/$graphQLPath", PermanentRedirect)
      }
  }
}
