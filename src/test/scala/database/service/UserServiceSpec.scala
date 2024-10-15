package database.service

import database.repository.UserRepositoryAlg
import database.schema.UserTableRow
import domain.User
import domain.error.{DatabaseTransactionError, UsernameDuplicateError}
import http.server.endpoint.HealthCheckEndpointsSpec.{suite, test}
import org.postgresql.util.{PSQLException, PSQLState}
import util.generators.Generators
import zio.*
import zio.jdbc.{ZConnection, ZConnectionPool}
import zio.test.*


object UserServiceSpec extends ZIOSpecDefault with Generators {

  private def mockUserRepo(
                        insertResponse: URIO[ZConnection, Long],
                        getUsersResponse: URIO[ZConnection, Chunk[UserTableRow]],
                        deleteUserResponse: URIO[ZConnection, Long]
                      ) = ZLayer.succeed(
    new UserRepositoryAlg:
      override def insertUser(user: User): URIO[ZConnection, Long] = insertResponse

      override def getAllUsers: URIO[ZConnection, Chunk[UserTableRow]] = getUsersResponse

      override def deleteUserByUsername(userName: String): URIO[ZConnection, Long] = deleteUserResponse
  )

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("UserService")(
      insertUserTests,
      getAllUsersTest,
      deleteUserByUsernameTest
    )

  private val insertUserTests = suite("insertUser")(
    test("calls UserRepository to persist the user - returns valid response") {
      checkN(10)(userGen) { user =>
        (for {
          insertUser <- ZIO.serviceWith[UserServiceAlg](_.insertUser)
          _ <- insertUser(user)
        } yield assertCompletes)
          .provide(
            mockUserRepo(
              insertResponse = ZIO.succeed(1L),
              getUsersResponse = ZIO.succeed(Chunk.empty),
              deleteUserResponse = ZIO.succeed(0L)
            ),
            ZConnectionPool.h2test,
            database.service.UserService.layer
          )
      }
    },
    test("calls UserRepository to persist the user - returns failure response") {
      checkN(10)(userGen) { user =>
        (for {
          underTest <- ZIO.service[UserServiceAlg]
          error <- underTest.insertUser(user).flip
        } yield assertTrue(
          error.isInstanceOf[UsernameDuplicateError],
          error.getMessage == "Something went wrong with the db")
          )
          .provide(
            mockUserRepo(
              insertResponse = ZIO.die(new PSQLException("Something went wrong with the db", PSQLState.UNIQUE_VIOLATION)),
              getUsersResponse = ZIO.succeed(Chunk.empty),
              deleteUserResponse = ZIO.succeed(0L)
            ),
            ZConnectionPool.h2test,
            database.service.UserService.layer
          )
      }
    }
  )

  private val getAllUsersTest = suite("getAllUsers")(
    test("calls UserRepository to get all the users - returns valid response") {
      checkN(10)(chunkUserTableGen) { userTableChunk =>
        (for {
          users <- ZIO.serviceWithZIO[UserServiceAlg](_.getAllUsers)
        } yield assertTrue(
          users.length == userTableChunk.length
        ))
          .provide(
            mockUserRepo(
              insertResponse = ZIO.succeed(1L),
              getUsersResponse = ZIO.succeed(userTableChunk),
              deleteUserResponse = ZIO.succeed(0L)
            ),
            ZConnectionPool.h2test,
            database.service.UserService.layer
          )
      }
    },
    test("calls UserRepository to get all the persisted users - returns failure response") {
      (for {
        error <- ZIO.serviceWithZIO[UserServiceAlg](_.getAllUsers).flip
      } yield assertTrue(
        error == DatabaseTransactionError("Something went wrong whilst getting all the users")
      ))
        .provide(
          mockUserRepo(
            insertResponse = ZIO.succeed(1L),
            getUsersResponse = ZIO.dieMessage("Something went wrong whilst getting all the users"),
            deleteUserResponse = ZIO.succeed(0L)
          ),
          ZConnectionPool.h2test,
          database.service.UserService.layer
        )
    }
  )

  private val deleteUserByUsernameTest = suite("deleteUserByUsername")(
    test("calls UserRepository to delete a user by username - returns valid response") {
      (for {
        _ <- ZIO.serviceWithZIO[UserServiceAlg](_.deleteUserByUsername("username"))
      } yield assertCompletes)
        .provide(
          mockUserRepo(
            insertResponse = ZIO.succeed(0L),
            getUsersResponse = ZIO.succeed(Chunk.empty),
            deleteUserResponse = ZIO.succeed(1L)
          ),
          ZConnectionPool.h2test,
          database.service.UserService.layer
        )
    },
    test("calls UserRepository to delete a user by username - returns failure response") {
      (for {
        error <- ZIO.serviceWithZIO[UserServiceAlg](_.deleteUserByUsername("username")).flip
      } yield assertTrue(
        error == DatabaseTransactionError("Something went wrong whilst deleting user")
      ))
        .provide(
          mockUserRepo(
            insertResponse = ZIO.succeed(1L),
            getUsersResponse = ZIO.succeed(Chunk.empty),
            deleteUserResponse = ZIO.dieMessage("Something went wrong whilst deleting user"),
          ),
          ZConnectionPool.h2test,
          database.service.UserService.layer
        )
    }
  )
}
