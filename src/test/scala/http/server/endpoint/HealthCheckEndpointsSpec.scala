package http.server.endpoint

import domain.response.*
import program.HealthProgramAlg
import zio.*
import zio.http.*
import zio.jdbc.ZConnectionPool
import zio.json.*
import zio.test.*


object HealthCheckEndpointsSpec extends ZIOSpecDefault {

  private def healthProgramMock(getStatusesResponse: ZIO[ZConnectionPool, Throwable, Map[String, String]]): ULayer[HealthProgramAlg] =
    ZLayer.succeed(
      new HealthProgramAlg {
        override def getStatuses: ZIO[ZConnectionPool, Throwable, Map[String, String]] = getStatusesResponse
      }
    )

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("HealthCheckEndpoints")(
      getStatusEndpointTests,
      getStatusDependenciesEndpointTests
    )

  private val getStatusEndpointTests = suite("get /status")(
    test("returns 200 and a proper json body") {
      for {
        routes <- ZIO.serviceWith[HealthCheckEndpointsAlg](_.routes)
        url <- ZIO.fromEither(URL.decode("/status"))
        validStatusRequest = Request(
          method = Method.GET,
          url = url
        )
        response <- routes.runZIO(validStatusRequest)
        body <- response.body.asString
        expected = SuccessfulResponse("Ok")
      } yield assertTrue(
        response.status == Status.Ok,
        body == expected.toJson
      )
    }.provide(
      ZConnectionPool.h2test,
      healthProgramMock(
        getStatusesResponse = ZIO.succeed(Map.empty)
      ),
      HealthCheckEndpoints.layer
    )
  )

  private val getStatusDependenciesEndpointTests = suite("get /status/dependencies")(
    test("returns 200 with a dependencies map") {
      for {
        routes <- ZIO.serviceWith[HealthCheckEndpointsAlg](_.routes)
        url <- ZIO.fromEither(URL.decode("/status/dependencies"))
        validStatusRequest = Request(
          method = Method.GET,
          url = url
        )
        expected = Map("dependency" -> "Ok")
        response <- routes.runZIO(validStatusRequest)
        body <- response.body.asString
      } yield assertTrue(
        response.status == Status.Ok,
        body == expected.toJson
      )
    }
  ).provide(
    ZConnectionPool.h2test,
    healthProgramMock(
      getStatusesResponse = ZIO.succeed(Map(
        "dependency" -> "Ok"
      ))
    ),
    HealthCheckEndpoints.layer,
  )

}
