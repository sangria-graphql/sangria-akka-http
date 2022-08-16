package sangria.http

import sangria.ast.Document

case class PreparedGraphQLRequest[T](query: Document, variables: T, operationName: Option[String])

object PreparedGraphQLRequest {
  def apply[T](query: Document, variables: Option[T], operationName: Option[String])(implicit
      v: Variables[T]): PreparedGraphQLRequest[T] =
    new PreparedGraphQLRequest(
      query = query,
      variables = variables.fold(v.empty)(identity),
      operationName = operationName
    )
}
