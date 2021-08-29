package sangria.http.akka

import akka.http.scaladsl.model.MediaTypes._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{
  Directive,
  ExceptionHandler,
  MalformedQueryParamRejection,
  MalformedRequestContentRejection,
  RejectionHandler,
  Route,
  StandardRoute
}
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, FromStringUnmarshaller}
import Util.explicitlyAccepts
import sangria.ast.Document
import sangria.parser.{QueryParser, SyntaxError}
import GraphQLRequestUnmarshaller._
import akka.http.javadsl.server.RequestEntityExpectedRejection
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.ContentType
import akka.http.scaladsl.model.StatusCodes.{
  BadRequest,
  InternalServerError,
  UnprocessableEntity
}
import akka.http.scaladsl.model.headers.`Content-Type`

import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

trait SangriaAkkaHttp[Input] {
  import SangriaAkkaHttp._

  type GQLRequestHandler = PartialFunction[Try[GraphQLRequest[Input]], StandardRoute]
  implicit def errorMarshaller: ToEntityMarshaller[GraphQLErrorResponse]
  implicit def requestUnmarshaller: FromEntityUnmarshaller[GraphQLHttpRequest[Input]]
  implicit def variablesUnmarshaller: FromStringUnmarshaller[Input]

  private val MISSING_QUERY_MSG =
    s"""Could not extract `query` from request.
       |Please confirm you have included a valid GraphQL query either as a QueryString parameter, or in the body of your request.""".stripMargin

  private val HELPFUL_UNPROCESSABLE_ERR = MalformedRequest(s"""
       |Check that you have provided well-formed JSON in the request.
       |`variables` must also be valid JSON if you have provided this
       |parameter to your request.""".stripMargin)

  def malformedRequestHandler: RejectionHandler =
    RejectionHandler
      .newBuilder()
      .handle {
        case r: MalformedQueryParamRejection =>
          val err = formatError(r.cause.getOrElse(MalformedRequest(r.errorMsg)))
          complete(
            UnprocessableEntity,
            GraphQLErrorResponse(
              errors = err :: formatError(HELPFUL_UNPROCESSABLE_ERR) :: Nil
            ))
        case r: MalformedRequestContentRejection =>
          val err = formatError(r.cause)
          complete(
            UnprocessableEntity,
            GraphQLErrorResponse(
              errors = err :: formatError(HELPFUL_UNPROCESSABLE_ERR) :: Nil
            ))
        case _: RequestEntityExpectedRejection =>
          val err = formatError(MalformedRequest(MISSING_QUERY_MSG))
          complete(
            BadRequest,
            GraphQLErrorResponse(
              errors = err :: Nil
            ))
      }
      .result()

  def graphQLExceptionHandler: ExceptionHandler =
    ExceptionHandler { case _ =>
      complete(
        InternalServerError,
        GraphQLErrorResponse(
          GraphQLError(
            "Internal Server Error"
          ) :: Nil
        ))
    }

  val graphQLPlayground: Route = get {
    explicitlyAccepts(`text/html`) {
      getFromResource("assets/playground.html")
    }
  }

  def formatError(error: Throwable): GraphQLError = error match {
    case syntaxError: SyntaxError =>
      GraphQLError(
        syntaxError.getMessage,
        Some(
          Location(
            syntaxError.originalError.position.line,
            syntaxError.originalError.position.column) :: Nil)
      )
    case NonFatal(e) =>
      GraphQLError(e.getMessage)
    case e =>
      throw e
  }

  /** Parse the given GraphQL query.
    *
    * @return [[Success]] containing the parsed query; or [[Failure]] containing a [[MalformedRequest]] exception
    *         if no query was present, or another exception if the query failed parsing
    */
  def prepareQuery(maybeQuery: Option[String]): Try[Document] = maybeQuery match {
    case Some(q) => QueryParser.parse(q)
    case None => Failure(MalformedRequest(MISSING_QUERY_MSG))
  }

  /** Extracts the standard GraphQL HTTP query parameters from an HTTP GET or POST.
    *
    * The extracted parameters are:
    *   1. `query`
    *   1. `operationName`
    *   1. `variables`
    *
    * @see https://graphql.org/learn/serving-over-http/#http-methods-headers-and-body
    */
  private[this] val extractParams: Directive[(Option[String], Option[String], Option[Input])] =
    parameters("query".?, "operationName".?, "variables".as[Input].?)

  /** Extracts the standard GraphQL parameters from an HTTP POST.
    *
    * The extracted parameters are:
    *   1. `query`
    *   1. `operationName`
    *   1. `variables`
    *
    * The parameters can come from either the HTTP query parameters or the message body,
    * with the former taking precedence.
    * The message body can be of either `application/json` or `application/graphql` content.
    *
    * This implementation differs from the
    * [[https://graphql.org/learn/serving-over-http/#post-request informal specification]] in that it also accepts
    * the operation name and/or variables as HTTP query parameters.
    *
    * @see https://graphql.org/learn/serving-over-http/#post-request
    */
  private[this] val extractPostParams: Directive[(Option[String], Option[String], Option[Input])] =
    extractParams.tflatMap { case (maybeQuery, maybeOperation, maybeVariables) =>
      /** Directive that returns the query parameters. */
      lazy val params = Directive[(Option[String], Option[String], Option[Input])] { inner => ctx =>
        inner((maybeQuery, maybeOperation, maybeVariables))(ctx)
      }

      if (maybeQuery.isDefined && maybeOperation.isDefined && maybeVariables.isDefined)
        // We have all the info in the HTTP query parameters; no need to parse the HTTP message body.
        params
      else
        optionalHeaderValueByType(`Content-Type`).flatMap {
          case Some(header) => header.contentType match {
            case ContentType(`application/json`) =>  // Try parsing the message body as JSON.
              entity(as[GraphQLHttpRequest[Input]]).map { body =>
                (maybeQuery orElse body.query, maybeOperation orElse body.operationName, maybeVariables orElse body.variables)
              }

            case ContentType(`application/graphql`) if maybeQuery.isEmpty =>  // Load the message body as the GraphQL query.
              entity(as[String]).map { body => (Option(body), maybeOperation, maybeVariables) }

            case _ => params  // All other content types are ignored.
          }
          case None => params  // No parseable message body.
        }
    }

  private def prepareGraphQLPost(inner: GQLRequestHandler)(implicit v: Variables[Input]): Route =
    extractParams { case (queryParam, operationNameParam, variablesParam) =>
      // Content-Type: application/json
      entity(as[GraphQLHttpRequest[Input]]) { body =>
        val maybeOperationName = operationNameParam.orElse(body.operationName)
        val maybeQuery = queryParam.orElse(body.query)

        // Variables may be provided in the QueryString, or possibly in the body as a String:
        // If we were unable to parse the variables from the body as a string,
        // we read them as JSON, and finally if no variables have been located
        // in the QueryString, Body (as a String) or Body (as JSON), we provide
        // an empty JSON object as the final result
        val maybeVariables = variablesParam.orElse(body.variables)

        prepareQuery(maybeQuery) match {
          case Success(document) =>
            val result = GraphQLRequest(
              query = document,
              variables = maybeVariables,
              operationName = maybeOperationName
            )
            inner(Success(result))
          case Failure(error) => inner(Failure(error))
        }
      } ~
        // Content-Type: application/graphql
        entity(as[Document]) { document =>
          val result = GraphQLRequest(
            query = document,
            variables = variablesParam,
            operationName = operationNameParam)
          inner(Success(result))
        }
    }

  private def prepareGraphQLGet(inner: GQLRequestHandler)(implicit v: Variables[Input]): Route =
    extractParams { (maybeQuery, maybeOperationName, maybeVariables) =>
      prepareQuery(maybeQuery) match {
        case Success(document) =>
          val result = GraphQLRequest(
            query = document,
            variables = maybeVariables,
            maybeOperationName
          )
          inner(Success(result))
        case Failure(error) => inner(Failure(error))
      }
    }

  def prepareGraphQLRequest(inner: GQLRequestHandler)(implicit v: Variables[Input]): Route =
    handleExceptions(graphQLExceptionHandler) {
      handleRejections(malformedRequestHandler) {
        get {
          prepareGraphQLGet(inner)
        } ~ post {
          prepareGraphQLPost(inner)
        }
      }
    }

  def graphQLRoute(inner: GQLRequestHandler)(implicit v: Variables[Input]): Route =
    path("graphql") {
      graphQLPlayground ~ prepareGraphQLRequest(inner)
    }
}

object SangriaAkkaHttp {
  //FIXME The name of this class should really be suffixed with `Exception`.
  final case class MalformedRequest(
      private val message: String = "Your request could not be processed",
      private val cause: Throwable = None.orNull
  ) extends Exception(message, cause)

  case class Location(line: Int, column: Int)
  case class GraphQLError(message: String, locations: Option[List[Location]] = None)
  case class GraphQLErrorResponse(errors: List[GraphQLError])
}
