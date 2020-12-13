package sangria.http.akka

import akka.http.scaladsl.model.{HttpMethods, HttpRequest}
import akka.http.scaladsl.model.StatusCodes.{BadRequest, InternalServerError, OK, PermanentRedirect}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import org.scalatest.Suite
import sangria.execution.{ErrorWithResolver, Executor, QueryAnalysisError}
import sangria.execution.deferred.DeferredResolver
import sangria.http.akka.SangriaAkkaHttp._
import sangria.http.akka.schema.{CharacterRepo, SchemaDefinition}
import sangria.http.akka.circe.CirceHttpSupport._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.Json
import sangria.marshalling.circe._

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

trait GraphQLHttpSpec { this: Suite =>
  implicit def executor: ExecutionContext

  val route: Route =
    path("graphql") {
      graphQLPlayground ~
      prepareGraphQLRequest[Json] {
        case Success(GraphQLRequest(query, variables, operationName)) =>
          val deferredResolver = DeferredResolver.fetchers(SchemaDefinition.characters)
          val graphQLResponse = Executor.execute(
            schema = SchemaDefinition.StarWarsSchema,
            queryAst = query,
            userContext = new CharacterRepo,
            variables = variables,
            operationName = operationName,
            deferredResolver = deferredResolver
          ).map(OK -> _)
            .recover {
              case error: QueryAnalysisError => BadRequest -> error.resolveError
              case error: ErrorWithResolver => InternalServerError -> error.resolveError
            }
          complete(graphQLResponse)
        case Failure(preparationError) => complete(BadRequest, formatError(preparationError))
      }
    } ~
    (get & pathEndOrSingleSlash) {
      redirect("/graphql", PermanentRedirect)
    }
}
