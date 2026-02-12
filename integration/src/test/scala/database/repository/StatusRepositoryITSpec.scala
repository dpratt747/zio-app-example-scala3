package database.repository

import _root_.util.*
import org.flywaydb.core.api.output.ValidateResult
import zio.*
import zio.jdbc.*
import zio.test.*

object StatusRepositoryITSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment & Scope, Throwable] =
    suite("StatusRepository")(
      test("can successfully query the db to check if it is live") {
        TestContainerResource.postgresResource.flatMap { postgresContainer =>
          (for {
            select1 <- ZIO.serviceWith[StatusRepositoryAlg](_.select1())
            flyway <- FlywayResource.flywayResource(postgresContainer.getJdbcUrl, postgresContainer.getUsername, postgresContainer.getPassword)
            validationResult <- ZIO.attempt(flyway.validateWithResult())
            underTest <- transaction(
              select1
            )
          } yield assertTrue(
            validationResult.validationSuccessful,
            underTest.contains(1)
          )).provide(
            ConnectionPoolConfigLayer(postgresContainer),
            ZLayer.succeed(ZConnectionPoolConfig.default),
            Scope.default,
            StatusRepository.layer
          )
        }
      }
    ) @@ TestAspect.timeout(zio.Duration.fromSeconds(150))
}
