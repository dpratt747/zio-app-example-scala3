package database.repository

import _root_.util.*
import database.schema.UserTableRow
import database.util.ZConnectionPoolWrapper
import domain.{PortDetails, User}
import org.flywaydb.core.api.output.ValidateResult
import org.testcontainers.containers
import zio.*
import zio.jdbc.*
import zio.test.*

object UserRepositoryITSpec extends ZIOSpecDefault {

  private def connectionPoolConfigLayer(postgresContainer: containers.PostgreSQLContainer[?]) = ZConnectionPoolWrapper.connectionPool(
    postgresContainer.getHost,
    postgresContainer.getMappedPort(PortDetails.PostgresPort.port),
    postgresContainer.getDatabaseName,
    postgresContainer.getUsername,
    postgresContainer.getPassword
  )

  override def spec: Spec[TestEnvironment & Scope, Any] = suite("UserRepository")(
    insertUserTest,
    getAllUsersTest,
    deleteUserByUsernameTest
  ) @@ TestAspect.timeout(zio.Duration.fromSeconds(35))


  private val getAllUsersTest = suite("getAllUsers")(
    test("can successfully get all users") {
      TestContainerResource.postgresResource.flatMap { postgresContainer =>
        (for {
          (insertUser, selectAll) <- ZIO.serviceWith[UserRepositoryAlg](service => (service.insertUser, service.getAllUsers))
          flyway <- FlywayResource.flywayResource(postgresContainer.getJdbcUrl, postgresContainer.getUsername, postgresContainer.getPassword)
          validationResult <- ZIO.attempt(flyway.validateWithResult())
          user1 = User("LimbMissing1", "David", "Pratt", None)
          user2 = User("LimbMissing2", "David", "Pratt", None)
          user3 = User("LimbMissing3", "David", "Pratt", Some("Address String"))
          underTest: Chunk[UserTableRow] <- transaction(
            insertUser(user1) *> insertUser(user2) *> insertUser(user3) *> selectAll
          )
        } yield assertTrue(
          validationResult.validationSuccessful,
          underTest.length == 3
        )).provide(
          connectionPoolConfigLayer(postgresContainer),
          ZLayer.succeed(ZConnectionPoolConfig.default),
          Scope.default,
          UserRepository.layer
        )
      }
    }
  )

  private val insertUserTest = suite("insertUser")(
    test("can successfully insert a user with address") {
      TestContainerResource.postgresResource.flatMap { postgresContainer =>
        (for {
          insertUser <- ZIO.serviceWith[UserRepositoryAlg](_.insertUser)
          flyway <- FlywayResource.flywayResource(postgresContainer.getJdbcUrl, postgresContainer.getUsername, postgresContainer.getPassword)
          validationResult <- ZIO.attempt(flyway.validateWithResult())
          user = User("LimbMissing", "David", "Pratt", Some("Address String"))
          selectSqlFrag = sql"select * from user_table".query[UserTableRow]
          underTest <- transaction(
            insertUser(user) *> selectSqlFrag.selectAll
          )
        } yield assertTrue(
          validationResult.validationSuccessful,
          underTest match {
            case Chunk(userTableRow) =>
              userTableRow.userName == user.userName &&
                userTableRow.firstName == user.firstName &&
                userTableRow.lastName == user.lastName &&
                userTableRow.maybeAddress == user.address
            case _ => false
          }
        )).provide(
          connectionPoolConfigLayer(postgresContainer),
          ZLayer.succeed(ZConnectionPoolConfig.default),
          Scope.default,
          UserRepository.layer
        )
      }
    },
    test("can successfully insert into a user") {
      TestContainerResource.postgresResource.flatMap { postgresContainer =>
        (for {
          insertUser <- ZIO.serviceWith[UserRepositoryAlg](_.insertUser)
          flyway <- FlywayResource.flywayResource(postgresContainer.getJdbcUrl, postgresContainer.getUsername, postgresContainer.getPassword)
          validationResult <- ZIO.attempt(flyway.validateWithResult())
          user = User("LimbMissing", "David", "Pratt", None)
          selectSqlFrag = sql"select * from user_table".query[UserTableRow]
          underTest <- transaction(
            insertUser(user) *> selectSqlFrag.selectAll
          )
        } yield assertTrue(
          validationResult.validationSuccessful,
          underTest match {
            case Chunk(userTableRow) =>
              userTableRow.userName == user.userName &&
                userTableRow.firstName == user.firstName &&
                userTableRow.lastName == user.lastName &&
                userTableRow.maybeAddress == None
            case _ => false
          }
        )).provide(
          connectionPoolConfigLayer(postgresContainer),
          ZLayer.succeed(ZConnectionPoolConfig.default),
          Scope.default,
          UserRepository.layer
        )
      }
    },
    test("errors when trying to use the same username more than once") {
      TestContainerResource.postgresResource.flatMap { postgresContainer =>
        (for {
          insertUser <- ZIO.serviceWith[UserRepositoryAlg](_.insertUser)
          flyway <- FlywayResource.flywayResource(postgresContainer.getJdbcUrl, postgresContainer.getUsername, postgresContainer.getPassword)
          validationResult <- ZIO.attempt(flyway.validateWithResult())
          user = User("LimbMissing", "David", "Pratt", None)
          error <- transaction(
            insertUser(user) *> insertUser(user)
          ).sandbox.flip
        } yield assertTrue(
          validationResult.validationSuccessful,
          error.squash.getMessage.contains("ERROR: duplicate key value violates unique constraint \"user_table_user_name_key\"")
        )).provide(
          connectionPoolConfigLayer(postgresContainer),
          ZLayer.succeed(ZConnectionPoolConfig.default),
          Scope.default,
          UserRepository.layer
        )
      }
    }
  )

  private val deleteUserByUsernameTest = suite("deleteUserByUsername")(
    test("can delete a user by username when it exists") {
      TestContainerResource.postgresResource.flatMap { postgresContainer =>
        (for {
          (insertUser, deleteUser) <- ZIO.serviceWith[UserRepositoryAlg](service => (service.insertUser, service.deleteUserByUsername))
          flyway <- FlywayResource.flywayResource(postgresContainer.getJdbcUrl, postgresContainer.getUsername, postgresContainer.getPassword)
          validationResult <- ZIO.attempt(flyway.validateWithResult())
          userName = "LimbMissing"
          user = User(userName, "David", "Pratt", None)
          result <- transaction(
            insertUser(user) *> deleteUser(userName)
          )
        } yield assertTrue(
          validationResult.validationSuccessful,
          result == 1L // number of records that have been changed
        )).provide(
          connectionPoolConfigLayer(postgresContainer),
          ZLayer.succeed(ZConnectionPoolConfig.default),
          Scope.default,
          UserRepository.layer
        )
      }
    },
    test("when calling delete user by username for a user that does not exist") {
      TestContainerResource.postgresResource.flatMap { postgresContainer =>
        (for {
          deleteUser <- ZIO.serviceWith[UserRepositoryAlg](_.deleteUserByUsername)
          flyway <- FlywayResource.flywayResource(postgresContainer.getJdbcUrl, postgresContainer.getUsername, postgresContainer.getPassword)
          validationResult <- ZIO.attempt(flyway.validateWithResult())
          userName = "LimbMissing"
          result <- transaction(
            deleteUser(userName)
          )
        } yield assertTrue(
          validationResult.validationSuccessful,
          result == 0L // number of records that have been changed
        )).provide(
          connectionPoolConfigLayer(postgresContainer),
          ZLayer.succeed(ZConnectionPoolConfig.default),
          Scope.default,
          UserRepository.layer
        )
      }
    }
  )
}
