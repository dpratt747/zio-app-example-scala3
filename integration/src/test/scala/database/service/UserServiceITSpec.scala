package database.service

import database.repository.UserRepository
import domain.User
import domain.error.{UserAlreadyDeletedError, UserNotFoundError, UsernameDuplicateError}
import util.{ConnectionPoolConfigLayer, FlywayResource, TestContainerResource}
import zio.jdbc.ZConnectionPoolConfig
import zio.test.{Spec, TestAspect, TestEnvironment, ZIOSpecDefault, assertTrue}
import zio.{Scope, ZIO, ZLayer}

object UserServiceITSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment & Scope, Any] = suite("UserService")(
    insertUserTests,
    getAllUsersTest,
    deleteUserByUsernameTest
  ) @@ TestAspect.timeout(zio.Duration.fromSeconds(150))

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
          res == 1L
        )).provide(
          ConnectionPoolConfigLayer(postgresContainer),
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
          ConnectionPoolConfigLayer(postgresContainer),
          ZLayer.succeed(ZConnectionPoolConfig.default),
          Scope.default,
          UserRepository.layer,
          UserService.layer
        )
      }
    }
  )

  private val getAllUsersTest = suite("getAllUsers")(
    test("can successfully retrieve inserted users if they are not deleted") {
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
          ConnectionPoolConfigLayer(postgresContainer),
          ZLayer.succeed(ZConnectionPoolConfig.default),
          Scope.default,
          UserRepository.layer,
          UserService.layer
        )
      }
    },
    test("can not retrieve inserted users if they are deleted") {
      TestContainerResource.postgresResource.flatMap { postgresContainer =>
        (for {
          flyway <- FlywayResource.flywayResource(postgresContainer.getJdbcUrl, postgresContainer.getUsername, postgresContainer.getPassword)
          validationResult <- ZIO.attempt(flyway.validateWithResult())
          userNameToDelete = "limbmissing1"
          user1 = User(userNameToDelete, "David", "Pratt", None)
          user2 = User("limbmissing2", "David", "Pratt", None)
          user3 = User("limbmissing3", "David", "Pratt", None)
          (insertUser, deleteUser, getAllUsers) <- ZIO.serviceWith[UserServiceAlg](service =>
            (service.insertUser, service.deleteUserByUsername, service.getAllUsers)
          )
          _ <- insertUser(user1)
          _ <- insertUser(user2)
          _ <- insertUser(user3)
          _ <- deleteUser(userNameToDelete)
          res <- getAllUsers
        } yield assertTrue(
          validationResult.validationSuccessful,
          res.length == 2
        )).provide(
          ConnectionPoolConfigLayer(postgresContainer),
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
          ConnectionPoolConfigLayer(postgresContainer),
          ZLayer.succeed(ZConnectionPoolConfig.default),
          Scope.default,
          UserRepository.layer,
          UserService.layer
        )
      }
    },
    test("errors when trying to delete a user that does not exist - [repo returns zero]") {
      TestContainerResource.postgresResource.flatMap { postgresContainer =>
        (for {
          flyway <- FlywayResource.flywayResource(postgresContainer.getJdbcUrl, postgresContainer.getUsername, postgresContainer.getPassword)
          validationResult <- ZIO.attempt(flyway.validateWithResult())
          deleteUserByUsername <- ZIO.serviceWith[UserServiceAlg](_.deleteUserByUsername)
          res <- deleteUserByUsername("notfound").flip
        } yield assertTrue(
          validationResult.validationSuccessful,
          res == UserNotFoundError("Unable to delete this user as the username does not exist")
        )).provide(
          ConnectionPoolConfigLayer(postgresContainer),
          ZLayer.succeed(ZConnectionPoolConfig.default),
          Scope.default,
          UserRepository.layer,
          UserService.layer
        )
      }
    },
    test("can not delete the same user twice") {
      TestContainerResource.postgresResource.flatMap { postgresContainer =>
        (for {
          flyway <- FlywayResource.flywayResource(postgresContainer.getJdbcUrl, postgresContainer.getUsername, postgresContainer.getPassword)
          validationResult <- ZIO.attempt(flyway.validateWithResult())
          user = User("limbmissing", "David", "Pratt", None)
          (insertUser, deleteUserByUsername) <- ZIO.serviceWith[UserServiceAlg](service => (service.insertUser, service.deleteUserByUsername))
          _ <- insertUser(user)
          _ <- deleteUserByUsername(user.userName)
          failure <- deleteUserByUsername(user.userName).flip
        } yield assertTrue(
          validationResult.validationSuccessful,
          failure.isInstanceOf[UserAlreadyDeletedError]
        )).provide(
          ConnectionPoolConfigLayer(postgresContainer),
          ZLayer.succeed(ZConnectionPoolConfig.default),
          Scope.default,
          UserRepository.layer,
          UserService.layer
        )
      }
    }
  )
}