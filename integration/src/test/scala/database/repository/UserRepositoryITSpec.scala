package database.repository

import _root_.util.*
import database.schema.UserTableRow
import domain.User
import org.flywaydb.core.api.output.ValidateResult
import org.postgresql.util.PSQLException
import zio.*
import zio.jdbc.*
import zio.test.*

object UserRepositoryITSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment & Scope, Any] = suite("UserRepository")(
    insertUserTest,
    getAllUsersTest,
    softDeleteUserByUserNameTest
  ) @@ TestAspect.timeout(zio.Duration.fromSeconds(150))


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
          ConnectionPoolConfigLayer(postgresContainer),
          ZLayer.succeed(ZConnectionPoolConfig.default),
          Scope.default,
          UserRepository.layer
        )
      }
    },
    test("can successfully get all users that are not deleted") {
      TestContainerResource.postgresResource.flatMap { postgresContainer =>
        (for {
          (insertUser, selectAll) <- ZIO.serviceWith[UserRepositoryAlg](service => (service.insertUser, service.getAllUsers))
          flyway <- FlywayResource.flywayResource(postgresContainer.getJdbcUrl, postgresContainer.getUsername, postgresContainer.getPassword)
          validationResult <- ZIO.attempt(flyway.validateWithResult())
          user1 = User("LimbMissing1", "David", "Pratt", None)
          user2 = User("LimbMissing2", "David", "Pratt", None)
          user3 = User("LimbMissing3", "David", "Pratt", Some("Address String"))
          all <- transaction(
            for {
              userId1 <- insertUser(user1)
              _ <- insertUser(user2)
              _ <- insertUser(user3)
              _ <- sql"INSERT INTO user_delete_table(user_id)".values(userId1).insert
              all <- selectAll
            } yield all
          )
        } yield assertTrue(
          validationResult.validationSuccessful,
          all.length == 2
        )).provide(
          ConnectionPoolConfigLayer(postgresContainer),
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
          (userId, all) <- transaction(
            for {
              userId <- insertUser(user)
              selectAll <- selectSqlFrag.selectAll
            } yield (userId, selectAll)
          )
        } yield assertTrue(
          validationResult.validationSuccessful,
          userId.contains(1),
          all match {
            case Chunk(userTableRow) =>
              userTableRow.userName == user.userName &&
                userTableRow.firstName == user.firstName &&
                userTableRow.lastName == user.lastName &&
                userTableRow.maybeAddress == user.address
            case _ => false
          }
        )).provide(
          ConnectionPoolConfigLayer(postgresContainer),
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
          (userId, all) <- transaction(
            for {
              userId <- insertUser(user)
              all <- selectSqlFrag.selectAll
            } yield (userId, all)
          )
        } yield assertTrue(
          validationResult.validationSuccessful,
          userId.contains(1),
          all match {
            case Chunk(userTableRow) =>
              userTableRow.userName == user.userName &&
                userTableRow.firstName == user.firstName &&
                userTableRow.lastName == user.lastName &&
                userTableRow.maybeAddress.isEmpty
            case _ => false
          }
        )).provide(
          ConnectionPoolConfigLayer(postgresContainer),
          ZLayer.succeed(ZConnectionPoolConfig.default),
          Scope.default,
          UserRepository.layer
        )
      }
    },
    test("inserting a user returns the userId") {
      TestContainerResource.postgresResource.flatMap { postgresContainer =>
        checkN(5)(Gen.uuid.map(_.toString), Gen.uuid.map(_.toString), Gen.uuid.map(_.toString), Gen.uuid.map(_.toString)) {
          (userName1, userName2, userName3, userName4) =>
            (for {
              insertUser <- ZIO.serviceWith[UserRepositoryAlg](_.insertUser)
              flyway <- FlywayResource.flywayResource(postgresContainer.getJdbcUrl, postgresContainer.getUsername, postgresContainer.getPassword)
              validationResult <- ZIO.attempt(flyway.validateWithResult())
              user = User("LimbMissing", "David", "Pratt", None)
              (userId1, userId2, userId3, userId4) <- transaction(
                for {
                  userId1 <- insertUser(user.copy(userName = userName1))
                  userId2 <- insertUser(user.copy(userName = userName2))
                  userId3 <- insertUser(user.copy(userName = userName3))
                  userId4 <- insertUser(user.copy(userName = userName4))
                } yield (userId1, userId2, userId3, userId4)
              )
            } yield assertTrue(
              validationResult.validationSuccessful,
              userId1.contains(1),
              userId2.contains(2),
              userId3.contains(3),
              userId4.contains(4)
            )).provide(
              ConnectionPoolConfigLayer(postgresContainer),
              ZLayer.succeed(ZConnectionPoolConfig.default),
              Scope.default,
              UserRepository.layer
            )
        }
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
          ConnectionPoolConfigLayer(postgresContainer),
          ZLayer.succeed(ZConnectionPoolConfig.default),
          Scope.default,
          UserRepository.layer
        )
      }
    }
  )

  private val softDeleteUserByUserNameTest = suite("deleteUserByUserName")(
    test("can delete a user by username") {
      TestContainerResource.postgresResource.flatMap { postgresContainer =>
        (for {
          (insertUser, softDelete, selectAll) <- ZIO.serviceWith[UserRepositoryAlg](service =>
            (service.insertUser, service.softDeleteByUserName, service.getAllUsers)
          )
          flyway <- FlywayResource.flywayResource(postgresContainer.getJdbcUrl, postgresContainer.getUsername, postgresContainer.getPassword)
          validationResult <- ZIO.attempt(flyway.validateWithResult())
          userName = "LimbMissing"
          user = User(userName, "David", "Pratt", None)
          result <- transaction(
            insertUser(user) *> softDelete(userName) *> selectAll
          )
        } yield assertTrue(
          validationResult.validationSuccessful,
          result.isEmpty
        )).provide(
          ConnectionPoolConfigLayer(postgresContainer),
          ZLayer.succeed(ZConnectionPoolConfig.default),
          Scope.default,
          UserRepository.layer
        )
      }
    },
    test("returns 0 when trying to delete a user that does not exist") {
      TestContainerResource.postgresResource.flatMap { postgresContainer =>
        (for {
          softDelete <- ZIO.serviceWith[UserRepositoryAlg](_.softDeleteByUserName)
          flyway <- FlywayResource.flywayResource(postgresContainer.getJdbcUrl, postgresContainer.getUsername, postgresContainer.getPassword)
          validationResult <- ZIO.attempt(flyway.validateWithResult())
          result <- transaction(softDelete("LimbMissing"))
        } yield assertTrue(
          validationResult.validationSuccessful,
          result == 0
        )).provide(
          ConnectionPoolConfigLayer(postgresContainer),
          ZLayer.succeed(ZConnectionPoolConfig.default),
          Scope.default,
          UserRepository.layer
        )
      }
    },
    test("errors when trying to delete the same user twice by username") {
      TestContainerResource.postgresResource.flatMap { postgresContainer =>
        (for {
          (insertUser, softDelete, selectAll) <- ZIO.serviceWith[UserRepositoryAlg](service =>
            (service.insertUser, service.softDeleteByUserName, service.getAllUsers)
          )
          flyway <- FlywayResource.flywayResource(postgresContainer.getJdbcUrl, postgresContainer.getUsername, postgresContainer.getPassword)
          validationResult <- ZIO.attempt(flyway.validateWithResult())
          userName = "SomeUserName"
          user = User(userName, "David", "Pratt", None)
          result <- transaction(
            insertUser(user) *> softDelete(userName) *> softDelete(userName) *> selectAll
          ).flip
        } yield assertTrue(
          validationResult.validationSuccessful,
          result.asInstanceOf[PSQLException].getSQLState == "23505",
          result.isInstanceOf[PSQLException] // number of records that have been changed
        )).provide(
          ConnectionPoolConfigLayer(postgresContainer),
          ZLayer.succeed(ZConnectionPoolConfig.default),
          Scope.default,
          UserRepository.layer
        )
      }
    }
  )
}
