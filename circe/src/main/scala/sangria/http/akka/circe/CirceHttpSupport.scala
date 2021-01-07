package sangria.http.akka.circe

import akka.http.scaladsl.marshalling._
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, _}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe._
import io.circe.generic.semiauto._
import sangria.http.akka.{GraphQLHttpRequest, SangriaAkkaHttp, Variables}
import sangria.marshalling.InputUnmarshaller
import sangria.marshalling.circe.CirceInputUnmarshaller

trait CirceHttpSupport extends SangriaAkkaHttp[Json] with FailFastCirceSupport {
  import SangriaAkkaHttp._

  implicit val locationEncoder: Encoder[Location] = deriveEncoder[Location]
  implicit val graphQLErrorEncoder: Encoder[GraphQLError] = deriveEncoder[GraphQLError]
  implicit val graphQLErrorResponseEncoder: Encoder[GraphQLErrorResponse] =
    deriveEncoder[GraphQLErrorResponse]

  implicit val graphQLRequestDecoder: Decoder[GraphQLHttpRequest[Json]] =
    deriveDecoder[GraphQLHttpRequest[Json]]

  implicit object JsonVariables extends Variables[Json] {
    override def empty: Json = Json.obj()
  }

  override implicit def errorMarshaller: ToEntityMarshaller[GraphQLErrorResponse] = marshaller
  override implicit def requestUnmarshaller: FromEntityUnmarshaller[GraphQLHttpRequest[Json]] =
    unmarshaller

  // TODO: This seems... awkward?
  import PredefinedFromStringUnmarshallers.{
    _fromStringUnmarshallerFromByteStringUnmarshaller => stringFromByteStringUm
  }
  override implicit def variablesUnmarshaller: FromStringUnmarshaller[Json] =
    stringFromByteStringUm(fromByteStringUnmarshaller[Json])

}
