package database.service

import database.repository.StatusRepositoryITSpec.{suite, test}
import database.repository.UserRepository
import database.util.ZConnectionPoolWrapper
import domain.error.UsernameDuplicateError
import domain.{PortDetails, User}
import org.testcontainers.containers
import util.{FlywayResource, TestContainerResource}
import zio.jdbc.ZConnectionPoolConfig
import zio.test.{Spec, TestAspect, TestEnvironment, ZIOSpecDefault, assertTrue}
import zio.{Scope, ZIO, ZLayer}

object UserServiceITSpec extends ZIOSpecDefault {


  private def connectionPoolConfigLayer(postgresContainer: containers.PostgreSQLContainer[?]) = ZConnectionPoolWrapper.connectionPool(
    postgresContainer.getHost,
    postgresContainer.getMappedPort(PortDetails.PostgresPort.port),
    postgresContainer.getDatabaseName,
    postgresContainer.getUsername,
    postgresContainer.getPassword
  )

  override def spec = suite("UserService")(
    insertUserTests,
    getAllUsersTest,
    deleteUserByUsernameTest
  ) @@ TestAspect.timeout(zio.Duration.fromSeconds(35))

  private val insertUserTests = suite("insertUser")(
    test("can successfully insert the user") {
      TestContainerResource.postgresResource.flatMap { postgresContainer =>
        (for {
          flyway <- FlywayResource.flywayResource(postgresContainer.getJdbcUrl, postgresContainer.getUsername, postgresContainer.getPassword)
          validationResult <- ZIO.attempt(flyway.validateWithResult())
          user = User("limbmissing", "David", "Pratt", None)
          res <- ZIO.serviceWithZIO[UserServiceAlg](_.insertUser(user))
        } yield assertTrue(
          validationResult.validationSuccessful,
          res == ()
        )).provide(
          connectionPoolConfigLayer(postgresContainer),
          ZLayer.succeed(ZConnectionPoolConfig.default),
          Scope.default,
          UserRepository.layer,
          UserService.layer
        )
      }
    },
    test("cannot insert the same username more than once") {
      TestContainerResource.postgresResource.flatMap { postgresContainer =>
        (for {
          flyway <- FlywayResource.flywayResource(postgresContainer.getJdbcUrl, postgresContainer.getUsername, postgresContainer.getPassword)
          validationResult <- ZIO.attempt(flyway.validateWithResult())
          user = User("limbmissing", "David", "Pratt", None)
          underTest <- ZIO.service[UserServiceAlg]
          _ <- underTest.insertUser(user)
          error <- underTest.insertUser(user).flip
        } yield assertTrue(
          validationResult.validationSuccessful,
          error.isInstanceOf[UsernameDuplicateError]
        )).provide(
          connectionPoolConfigLayer(postgresContainer),
          ZLayer.succeed(ZConnectionPoolConfig.default),
          Scope.default,
          UserRepository.layer,
          UserService.layer
        )
      }
    }
  )

  private val getAllUsersTest = suite("getAllUsers")(
    test("can successfully retrieve inserted users") {
      TestContainerResource.postgresResource.flatMap { postgresContainer =>
        (for {
          flyway <- FlywayResource.flywayResource(postgresContainer.getJdbcUrl, postgresContainer.getUsername, postgresContainer.getPassword)
          validationResult <- ZIO.attempt(flyway.validateWithResult())
          user1 = User("limbmissing1", "David", "Pratt", None)
          user2 = User("limbmissing2", "David", "Pratt", None)
          user3 = User("limbmissing3", "David", "Pratt", None)
          (insertUser, getAllUsers) <- ZIO.serviceWith[UserServiceAlg](service => (service.insertUser, service.getAllUsers))
          _ <- insertUser(user1)
          _ <- insertUser(user2)
          _ <- insertUser(user3)
          res <- getAllUsers
        } yield assertTrue(
          validationResult.validationSuccessful,
          res.length == 3
        )).provide(
          connectionPoolConfigLayer(postgresContainer),
          ZLayer.succeed(ZConnectionPoolConfig.default),
          Scope.default,
          UserRepository.layer,
          UserService.layer
        )
      }
    }
  )

  private val deleteUserByUsernameTest = suite("deleteUserByUsername")(
    test("can delete a user by username") {
      TestContainerResource.postgresResource.flatMap { postgresContainer =>
        (for {
          flyway <- FlywayResource.flywayResource(postgresContainer.getJdbcUrl, postgresContainer.getUsername, postgresContainer.getPassword)
          validationResult <- ZIO.attempt(flyway.validateWithResult())
          user = User("limbmissing", "David", "Pratt", None)
          (insertUser, deleteUserByUsername) <- ZIO.serviceWith[UserServiceAlg](service => (service.insertUser, service.deleteUserByUsername))
          _ <- insertUser(user)
          res <- deleteUserByUsername(user.userName)
        } yield assertTrue(
          validationResult.validationSuccessful,
          res == ()
        )).provide(
          connectionPoolConfigLayer(postgresContainer),
          ZLayer.succeed(ZConnectionPoolConfig.default),
          Scope.default,
          UserRepository.layer,
          UserService.layer
        )
      }
    }
  )
}