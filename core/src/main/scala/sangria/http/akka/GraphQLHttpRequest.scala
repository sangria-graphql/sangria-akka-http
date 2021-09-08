package sangria.http.akka

/** A GraphQL request via an HTTP POST.
  *
  * @param query
  *   A [[https://spec.graphql.org/June2018/#Document GraphQL Document]].
  *   A Document must be present either here, or in the URI query parameter of the POST
  *   (in which case it's optional here and will be overridden if present).
  * @param operationName
  *   The name of the Operation in the Document to execute.
  *   [[https://spec.graphql.org/June2018/#sec-Executing-Requests Required if the Document contains more than one operation.]]
  * @param variables Values for any Variables defined by the Operation.
  *
  * @see https://spec.graphql.org/June2018/#sec-Execution
  * @see https://graphql.org/learn/serving-over-http/#post-request
  */
case class GraphQLHttpRequest[T](query: Option[String], variables: Option[T], operationName: Option[String])
