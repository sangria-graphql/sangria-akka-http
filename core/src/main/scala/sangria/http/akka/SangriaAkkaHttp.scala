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
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, FromRequestUnmarshaller, FromStringUnmarshaller, Unmarshaller}
import Util.explicitlyAccepts
import sangria.ast.Document
import sangria.parser.{QueryParser, SyntaxError}
import GraphQLRequestUnmarshaller._
import akka.http.javadsl.server.RequestEntityExpectedRejection
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.model.StatusCodes.{
  BadRequest,
  InternalServerError,
  UnprocessableEntity
}

import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

trait SangriaAkkaHttp[Input] {
  import SangriaAkkaHttp._

  type GQLRequestHandler = PartialFunction[Try[GraphQLRequest[Input]], StandardRoute]
  implicit def errorMarshaller: ToEntityMarshaller[GraphQLErrorResponse]

  /** Akka HTTP unmarshaller for a GraphQL request from an [[akka.http.scaladsl.model.HttpRequest]].
   *
   * The object returned from the unmarshaller is guaranteed to have a non-blank `query`.
   * The returned unmarshaller throws the [[Unmarshaller.NoContentException]]
   * if no GraphQL request was found or if its query was blank.
   *
   * @see https://graphql.org/learn/serving-over-http/#http-methods-headers-and-body
   */
  private[this]
  /* Comment out while I write the tests
  implicit
  */
  val httpRequestUnmarshaller: FromRequestUnmarshaller[GraphQLHttpRequest[Input]] =
      Unmarshaller.withMaterializer { implicit ec => implicit mat => request =>
        /* This is a bit more complicated than a typical HTTP request unmarshaller because we have to check the URI
         * query parameters. */
        val query = request.uri.query()
        val maybeQueryParam = query.get("query")

        val parsedRequest: Future[GraphQLHttpRequest[Input]] = request.method match {
          case HttpMethods.GET =>
            val futureVariables = query.get("variables")
              .map(variablesUnmarshaller(_).map(Some(_)))
              .getOrElse(Future.successful(None))

            futureVariables.map(v => GraphQLHttpRequest(
              query = maybeQueryParam,
              variables = v,
              operationName = query.get("operationName")
            ))

          case HttpMethods.POST =>
            val entity = request.entity
            entity.contentType.mediaType match {
              case `application/json` =>
                try { requestUnmarshaller(entity) }
                catch {
                  case Unmarshaller.NoContentException =>  // Empty HTTP message body is OK for now.
                    Future.successful(GraphQLHttpRequest[Input](None, None, None))
                }

              case `application/graphql` if maybeQueryParam.isEmpty => // Parse only if the URI `query` param is absent.
                val charset = `application/graphql`.charset.nioCharset()
                Unmarshaller.byteStringUnmarshaller.map(bs =>
                  GraphQLHttpRequest[Input](query = Some(bs.decodeString(charset)), None, None)
                )(entity)

              case _ =>
                Future.successful(GraphQLHttpRequest[Input](None, None, None))
            }
        }
        parsedRequest.map { r =>
          // Substitute in the URI `query` parameter, if present.
          val newR = maybeQueryParam.map(q => r.copy(query = Some(q))).getOrElse(r)

          // Check that the query is non-blank.
          if (newR.query.forall(_.isBlank))
            throw Unmarshaller.NoContentException
          else
            newR
        }
      }

  /*FIXME This name is ambiguous. It's a *GraphQL*, not HTTP, request unmarshaller. */
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
