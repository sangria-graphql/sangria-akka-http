package sangria.http.akka

import akka.http.scaladsl.model.MediaTypes._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{
  ExceptionHandler,
  MalformedQueryParamRejection,
  MalformedRequestContentRejection,
  RejectionHandler,
  Route,
  StandardRoute
}
import akka.http.scaladsl.unmarshalling.{FromRequestUnmarshaller, FromStringUnmarshaller}
import Util.explicitlyAccepts
import sangria.ast.Document
import sangria.parser.{QueryParser, SyntaxError}
import GraphQLRequestUnmarshaller._
import akka.http.javadsl.server.RequestEntityExpectedRejection
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.StatusCodes.{BadRequest, InternalServerError, UnprocessableEntity}

import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

object SangriaAkkaHttp {
  private val MISSING_QUERY_MSG =
    s"""Could not extract `query` from request.
       |Please confirm you have included a valid GraphQL query either as a QueryString parameter, or in the body of your request.""".stripMargin

  private val HELPFUL_UNPROCESSABLE_ERR = MalformedRequest(s"""
       |Check that you have provided well-formed JSON in the request.
       |`variables` must also be valid JSON if you have provided this
       |parameter to your request.""".stripMargin)

  def malformedRequestHandler(implicit
      errorUm: ToEntityMarshaller[GraphQLErrorResponse]): RejectionHandler =
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

  implicit def graphQLExceptionHandler(implicit
      errorUm: ToEntityMarshaller[GraphQLErrorResponse]): ExceptionHandler =
    ExceptionHandler { case _ =>
      complete(
        InternalServerError,
        GraphQLErrorResponse(
          GraphQLError(
            "Internal Server Error"
          ) :: Nil
        ))
    }

  final case class MalformedRequest(
      private val message: String = "Your request could not be processed",
      private val cause: Throwable = None.orNull)
      extends Exception(message, cause)

  case class Location(line: Int, column: Int)
  case class GraphQLError(message: String, locations: Option[List[Location]] = None)
  case class GraphQLErrorResponse(errors: List[GraphQLError])

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

  def prepareQuery(maybeQuery: Option[String]): Try[Document] = maybeQuery match {
    case Some(q) => QueryParser.parse(q)
    case None => Left(MalformedRequest(MISSING_QUERY_MSG)).toTry
  }

  private def prepareGraphQLPost[T](inner: Try[GraphQLRequest[T]] => StandardRoute)(implicit
      reqUm: FromRequestUnmarshaller[GraphQLHttpRequest[T]],
      varUm: FromStringUnmarshaller[T],
      v: Variables[T]): Route =
    parameters(Symbol("query").?, Symbol("operationName").?, Symbol("variables").as[T].?) {
      case (queryParam, operationNameParam, variablesParam) =>
        // Content-Type: application/json
        entity(as[GraphQLHttpRequest[T]]) { body =>
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

  private def prepareGraphQLGet[T](inner: Try[GraphQLRequest[T]] => StandardRoute)(implicit
      varUm: FromStringUnmarshaller[T],
      v: Variables[T]): Route =
    parameters(Symbol("query").?, Symbol("operationName").?, Symbol("variables").as[T].?) {
      (maybeQuery, maybeOperationName, maybeVariables) =>
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

  def prepareGraphQLRequest[T](
      inner: PartialFunction[Try[GraphQLRequest[T]], StandardRoute])(implicit
      reqUm: FromRequestUnmarshaller[GraphQLHttpRequest[T]],
      varUm: FromStringUnmarshaller[T],
      v: Variables[T],
      errorUm: ToEntityMarshaller[GraphQLErrorResponse]): Route =
    handleExceptions(graphQLExceptionHandler) {
      handleRejections(malformedRequestHandler) {
        get {
          prepareGraphQLGet(inner)
        } ~ post {
          prepareGraphQLPost(inner)
        }
      }
    }

  /** A complete route for simple out of the box GraphQL
    * @param inner
    * @param reqUm
    * @param varUm
    * @param v
    * @tparam T
    * @return
    */
  def graphQLRoute[T](inner: PartialFunction[Try[GraphQLRequest[T]], StandardRoute])(implicit
      reqUm: FromRequestUnmarshaller[GraphQLHttpRequest[T]],
      varUm: FromStringUnmarshaller[T],
      v: Variables[T],
      errorUm: ToEntityMarshaller[GraphQLErrorResponse]): Route =
    path("graphql") {
      optionalHeaderValueByName("X-Apollo-Tracing") { tracing =>
        graphQLPlayground ~
          prepareGraphQLRequest(inner)
      }
    }
}
