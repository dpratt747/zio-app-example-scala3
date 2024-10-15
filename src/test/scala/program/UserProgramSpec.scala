package program

import database.service.{StatusServiceAlg, UserServiceAlg}
import domain.User
import domain.error.*
import util.generators.Generators
import zio.*
import zio.jdbc.ZConnectionPool
import zio.test.*

object UserProgramSpec extends ZIOSpecDefault with Generators {

  private def mockUserServiceAlg(
                                  insertResponse: ZIO[ZConnectionPool, ServiceError, Unit],
                                  getUsersResponse: ZIO[ZConnectionPool, ServiceError, Chunk[User]],
                                  deleteUserByUsernameResponse: ZIO[ZConnectionPool, ServiceError, Unit]
                                ) = ZLayer.succeed(
    new UserServiceAlg {
      override def insertUser(user: User): ZIO[ZConnectionPool, ServiceError, Unit] = insertResponse

      override def getAllUsers: ZIO[ZConnectionPool, ServiceError, Chunk[User]] = getUsersResponse

      override def deleteUserByUsername(userName: String): ZIO[ZConnectionPool, ServiceError, Unit] = deleteUserByUsernameResponse
    }
  )

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("UserProgram")(
      insertUserTests,
      getAllUsersTests,
      deleteUserByUsernameTests
    )

  private val insertUserTests = suite("insertUser")(
    test("returns unit when the insertion is successful") {
      checkN(10)(userGen) { user =>
        for {
          _ <- ZIO.serviceWithZIO[UserProgramAlg](_.insertUser(user))
        } yield assertCompletes
      }
    }.provide(
      mockUserServiceAlg(
        insertResponse = ZIO.unit,
        getUsersResponse = ZIO.succeed(Chunk.empty),
        deleteUserByUsernameResponse = ZIO.unit
      ),
      UserProgram.layer,
      ZConnectionPool.h2test
    ),
    test("returns database service error when the insertion is not successful due to a username duplication error") {
      checkN(10)(userGen) { user =>
        for {
          insertUser <- ZIO.serviceWith[UserProgramAlg](_.insertUser)
          error <- insertUser(user).flip
        } yield assertTrue(
          error == UsernameDuplicateError("username is not unique error")
        )
      }
    }.provide(
      mockUserServiceAlg(
        insertResponse = ZIO.fail(UsernameDuplicateError("username is not unique error")),
        getUsersResponse = ZIO.succeed(Chunk.empty),
        deleteUserByUsernameResponse = ZIO.unit
      ),
      UserProgram.layer,
      ZConnectionPool.h2test
    )
  )

  private val getAllUsersTests = suite("getAllUsersTests")(
    test("returns a list of users when the query is successful") {
      for {
        users <- ZIO.serviceWithZIO[UserProgramAlg](_.getAllUsers)
      } yield assertTrue(
        users.length == 2
      )
    }.provide(
      mockUserServiceAlg(
        insertResponse = ZIO.unit,
        getUsersResponse = ZIO.succeed(Chunk(
          User("username1", "firstname1", "lastname1", None),
          User("username2", "firstname2", "lastname2", None)
        )),
        deleteUserByUsernameResponse = ZIO.unit
      ),
      UserProgram.layer,
      ZConnectionPool.h2test
    )
  )

  private val deleteUserByUsernameTests = suite("deleteUserByUsername")(
    test("returns unit when the deletion is successful") {
      checkN(10)(userGen) { user =>
        for {
          _ <- ZIO.serviceWithZIO[UserProgramAlg](_.deleteUserByUsername("username"))
        } yield assertCompletes
      }
    }.provide(
      mockUserServiceAlg(
        insertResponse = ZIO.unit,
        getUsersResponse = ZIO.succeed(Chunk.empty),
        deleteUserByUsernameResponse = ZIO.unit
      ),
      UserProgram.layer,
      ZConnectionPool.h2test
    ),
    test("returns database service error when the insertion is not successful due to a database transaction error") {
      for {
        error <- ZIO.serviceWithZIO[UserProgramAlg](_.deleteUserByUsername("username")).flip
      } yield assertTrue(
        error == DatabaseTransactionError("issue with transaction")
      )
    }.provide(
      mockUserServiceAlg(
        insertResponse = ZIO.unit,
        getUsersResponse = ZIO.succeed(Chunk.empty),
        deleteUserByUsernameResponse = ZIO.fail(DatabaseTransactionError("issue with transaction"))
      ),
      UserProgram.layer,
      ZConnectionPool.h2test
    )
  )
}
