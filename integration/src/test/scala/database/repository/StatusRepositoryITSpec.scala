package database.repository

import _root_.util.*
import database.repository.{StatusRepository, StatusRepositoryAlg}
import database.util.ZConnectionPoolWrapper
import domain.PortDetails
import org.flywaydb.core.api.output.ValidateResult
import org.testcontainers.containers
import zio.*
import zio.jdbc.*
import zio.test.*

object StatusRepositoryITSpec extends ZIOSpecDefault {

  private def connectionPoolConfigLayer(postgresContainer: containers.PostgreSQLContainer[?]) = ZConnectionPoolWrapper.connectionPool(
    postgresContainer.getHost,
    postgresContainer.getMappedPort(PortDetails.PostgresPort.port),
    postgresContainer.getDatabaseName,
    postgresContainer.getUsername,
    postgresContainer.getPassword
  )

  override def spec =
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
            connectionPoolConfigLayer(postgresContainer),
            ZLayer.succeed(ZConnectionPoolConfig.default),
            Scope.default,
            StatusRepository.layer
          )
        }
      }
    ) @@ TestAspect.timeout(zio.Duration.fromSeconds(35))
}
