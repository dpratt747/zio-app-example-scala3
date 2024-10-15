package database.service

import database.repository.StatusRepositoryAlg
import http.server.endpoint.HealthCheckEndpointsSpec.{suite, test}
import util.generators.Generators
import zio.*
import zio.jdbc.{ZConnection, ZConnectionPool}
import zio.test.*


object StatusServiceSpec extends ZIOSpecDefault with Generators {

  private def mockRepo(select1Response: URIO[ZConnection, Option[Int]]) = ZLayer.succeed(
    new StatusRepositoryAlg:
      override def select1(): URIO[ZConnection, Option[Int]] = select1Response
  )

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("StatusService")(
      isDBLiveTests
    )

  private val isDBLiveTests = suite("isDBLive")(
    test("returns true when the repository responds with Some(1)") {
      (for {
        isDBLive <- ZIO.serviceWithZIO[StatusServiceAlg](_.isDBLive)
      } yield assertTrue(isDBLive))
        .provide(
          mockRepo(
            select1Response = ZIO.succeed(Option(1))
          ),
          ZConnectionPool.h2test,
          StatusService.layer
        )
    },
    test("returns false when the repository responds with None") {
      (for {
        isDBLive <- ZIO.serviceWithZIO[StatusServiceAlg](_.isDBLive)
      } yield assertTrue(!isDBLive))
        .provide(
          mockRepo(
            select1Response = ZIO.none
          ),
          ZConnectionPool.h2test,
          StatusService.layer
        )
    }
  )
}
