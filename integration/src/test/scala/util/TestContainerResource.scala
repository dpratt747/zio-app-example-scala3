package util

import com.dimafeng.testcontainers.PostgreSQLContainer
import domain.PortDetails
import org.testcontainers.postgresql
import zio.*

object TestContainerResource {

  private val initAndStartPostgres: ZIO[Any, Throwable, postgresql.PostgreSQLContainer] = for {
    container: postgresql.PostgreSQLContainer <- ZIO.attempt(PostgreSQLContainer().container)
    _ <- ZIO.attempt(container.start())
    _ <- ZIO.logInfo(s"Starting PostgreSQL test-container instance. Url: [${container.getJdbcUrl}]" +
      s" Port: [${container.getMappedPort(PortDetails.PostgresPort.port)}]")
  } yield container

  val postgresResource: ZIO[Any & Scope, Throwable, postgresql.PostgreSQLContainer] =
    ZIO.acquireRelease(initAndStartPostgres)(postgresSQLContainer =>
        ZIO.attempt(postgresSQLContainer.stop()).tapError(e =>
          ZIO.logError(s"Something went wrong ${e.getMessage}")
        ).orDie
      )
      .zipLeft(ZIO.logInfo("Stopping PostgreSQL server"))
      .tapError(t =>
        ZIO.logErrorCause(s"Failed to stop PostgreSQL server", Cause.die(t)))

}
