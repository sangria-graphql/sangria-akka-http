package sangria.http.akka

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import io.circe.Json
import org.scalatest.{FlatSpec, Matchers}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.optics.JsonPath._

class CirceGraphQLTest extends FlatSpec with Matchers with GraphQLHttpSpec with ScalatestRouteTest {

  val simpleQuery =
    """query HeroAndFriends {
      |  hero {
      |    name
      |    friends {
      |      name
      |    }
      |  }
      |}
      |""".stripMargin

  val queryWithVariables =
    """query VariableExample($humanId: String!){
      |  human(id: $humanId) {
      |    name,
      |    homePlanet,
      |    friends {
      |      name
      |    }
      |  }
      |}
      |""".stripMargin

  val dataOptic = root.data.obj
  val errorsOptic = root.errors.arr

  it should "handle a graphQl request as a POST" in {
    Post("/graphql", Json.obj("query" -> Json.fromString(simpleQuery))) ~> route ~> check {
      status shouldEqual StatusCodes.OK
      val resp = responseAs[Json]

      dataOptic.getOption(resp).isDefined shouldBe true
      errorsOptic.getOption(resp).isDefined shouldBe false
      root.data.hero.name.string.getOption(resp) shouldBe Some("R2-D2")
    }
  }

  it should "handle a graphQl request with variables as a POST" in {
    val body = Json.obj(
      "query" -> Json.fromString(queryWithVariables),
      "variables" -> Json.obj("humanId" -> Json.fromString("1000"))
    )

    Post("/graphql", body) ~> route ~> check {
      status shouldEqual StatusCodes.OK
      val resp = responseAs[Json]

      dataOptic.getOption(resp).isDefined shouldBe true
      errorsOptic.getOption(resp).isDefined shouldBe false
      root.data.human.name.string.getOption(resp) shouldBe Some("Luke Skywalker")
    }
  }

  it should "handle a graphQl request with missing variables as a POST" in {
    val body = Json.obj(
      "query" -> Json.fromString(queryWithVariables)
    )

    Post("/graphql", body) ~> route ~> check {
      status shouldEqual StatusCodes.BadRequest
      val resp = responseAs[Json]

      dataOptic.getOption(resp).isDefined shouldBe false
      errorsOptic.getOption(resp).isDefined shouldBe true
      val messages = root.errors.each.message.string.getAll(resp)
      messages.head should startWith("Variable '$humanId' expected value of type 'String!' but value is undefined.")
    }
  }

}
