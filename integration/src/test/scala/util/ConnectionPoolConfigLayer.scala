package util

import database.util.ZConnectionPoolWrapper
import domain.PortDetails
import org.testcontainers.postgresql.PostgreSQLContainer
import zio.ZLayer
import zio.jdbc.{ZConnectionPool, ZConnectionPoolConfig}

object ConnectionPoolConfigLayer {
  def apply(postgresContainer: PostgreSQLContainer): ZLayer[ZConnectionPoolConfig, Throwable, ZConnectionPool] = ZConnectionPoolWrapper.connectionPool(
    postgresContainer.getHost,
    postgresContainer.getMappedPort(PortDetails.PostgresPort.port),
    postgresContainer.getDatabaseName,
    postgresContainer.getUsername,
    postgresContainer.getPassword
  )
}
