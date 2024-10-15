package http.server.endpoint

import domain.User
import domain.error.*
import domain.payload.CreateUserPayload
import domain.response.{AllUsersResponse, SuccessfulResponse}
import program.UserProgramAlg
import util.generators.Generators
import zio.*
import zio.http.*
import zio.jdbc.ZConnectionPool
import zio.json.*
import zio.json.ast.Json
import zio.test.*


object UserEndpointsSpec extends ZIOSpecDefault with Generators {

  private def userProgramMock(
                               insertUserResponse: ZIO[Any, ServiceError, Unit],
                               getAllUsersResponse: ZIO[Any, ServiceError, Chunk[User]],
                               deleteUserByUsernameResponse: ZIO[Any, ServiceError, Unit],
                             ): ULayer[UserProgramAlg] =
    ZLayer.succeed(
      new UserProgramAlg {
        override def insertUser(user: User): ZIO[Any, ServiceError, Unit] = insertUserResponse

        override def getAllUsers: ZIO[ZConnectionPool, ServiceError, Chunk[User]] = getAllUsersResponse

        override def deleteUserByUsername(userName: String): ZIO[ZConnectionPool, ServiceError, Unit] = deleteUserByUsernameResponse
      }
    )

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("UserEndpoints")(
      insertUserEndpointTests,
      getAllUsersEndpointTests,
      deleteUserEndpointTests
    )

  private val insertUserEndpointTests = suite("post /user")(
    test("returns 400 (Bad Request) when a user is inserted with an empty field [Username]") {
      for {
        routes <- ZIO.serviceWith[UserEndpointsAlg](_.routes)
        bodyString =
          """
            |{
            | "userName":"",
            | "firstName":"firstName",
            | "lastName":"lastName"
            |}
            |""".stripMargin
        url <- ZIO.fromEither(URL.decode("/user"))
        request = Request(
          method = Method.POST,
          url = url,
          body = Body.fromString(bodyString)
        )
        response <- routes.runZIO(request)
        body <- response.body.asString
        expectedString = """{"name":"MalformedBody","message":"Malformed request body failed to decode: .userName(String should not be empty)"}"""
      } yield assertTrue(
        response.status == Status.BadRequest,
        body == expectedString
      )
    }.provide(
      userProgramMock(
        insertUserResponse = ZIO.unit,
        getAllUsersResponse = ZIO.succeed(Chunk.empty),
        deleteUserByUsernameResponse = ZIO.unit
      ),
      UserEndpoints.layer,
      ZConnectionPool.h2test
    ),
    test("returns 201 when provided the proper payload and is successfully inserted") {
      checkN(1)(nonEmptyCreateUserPayload) { createUserPayload =>
        for {
          routes <- ZIO.serviceWith[UserEndpointsAlg](_.routes)
          url <- ZIO.fromEither(URL.decode("/user"))
          request = Request(
            method = Method.POST,
            url = url,
            body = Body.fromString(createUserPayload.toJson)
          )
          response <- routes.runZIO(request)
          body <- response.body.asString
          expected = SuccessfulResponse("success")
        } yield assertTrue(
          response.status == Status.Created,
          body == expected.toJson
        )
      }
    }.provide(
      userProgramMock(
        insertUserResponse = ZIO.unit,
        getAllUsersResponse = ZIO.succeed(Chunk.empty),
        deleteUserByUsernameResponse = ZIO.unit
      ),
      UserEndpoints.layer,
      ZConnectionPool.h2test
    ),
    test("returns 500 when the program fibre fails") {
      checkN(10)(nonEmptyCreateUserPayload) { createUserPayload =>
        for {
          routes <- ZIO.serviceWith[UserEndpointsAlg](_.routes)
          url <- ZIO.fromEither(URL.decode("/user"))
          request = Request(
            method = Method.POST,
            url = url,
            body = Body.fromString(createUserPayload.toJson)
          )
          response <- routes.runZIO(request)
        } yield assertTrue(
          response.status == Status.InternalServerError
        )
      }
    }.provide(
      ZConnectionPool.h2test,
      userProgramMock(
        insertUserResponse = ZIO.dieMessage("Program error"),
        getAllUsersResponse = ZIO.succeed(Chunk.empty),
        deleteUserByUsernameResponse = ZIO.unit
      ),
      UserEndpoints.layer
    )
  )

  private val getAllUsersEndpointTests = suite("get /users")(
    test("returns 200 when a request is made to get all users and no error occurs") {
      for {
        routes <- ZIO.serviceWith[UserEndpointsAlg](_.routes)
        url <- ZIO.fromEither(URL.decode("/users"))
        request = Request(
          method = Method.GET,
          url = url
        )
        response <- routes.runZIO(request)
        body <- response.body.asString
        expected = AllUsersResponse(Chunk.succeed(User("username", "firstname", "lastname", None)))
      } yield assertTrue(
        response.status == Status.Ok,
        body == expected.toJson
      )
    }.provide(
      userProgramMock(
        insertUserResponse = ZIO.unit,
        getAllUsersResponse = ZIO.succeed(Chunk.succeed(User("username", "firstname", "lastname", None))),
        deleteUserByUsernameResponse = ZIO.unit
      ),
      UserEndpoints.layer,
      ZConnectionPool.h2test
    ),
    test("returns 500 when the program fails") {
      for {
        routes <- ZIO.serviceWith[UserEndpointsAlg](_.routes)
        url <- ZIO.fromEither(URL.decode("/users"))
        request = Request(
          method = Method.GET,
          url = url
        )
        response <- routes.runZIO(request)
      } yield assertTrue(
        response.status == Status.InternalServerError,
      )
    }.provide(
      ZConnectionPool.h2test,
      userProgramMock(
        insertUserResponse = ZIO.unit,
        getAllUsersResponse = ZIO.dieMessage("Failed to retrieve all users"),
        deleteUserByUsernameResponse = ZIO.unit
      ),
      UserEndpoints.layer
    )
  )

  private val deleteUserEndpointTests = suite("delete /users")(
    test("returns 200 when a request is made to delete users and no error occurs") {
      for {
        routes <- ZIO.serviceWith[UserEndpointsAlg](_.routes)
        urlDecode <- ZIO.fromEither(URL.decode("/user"))
        queryParams = QueryParams.apply(("userName", "LimbMissing"))
        url = urlDecode.copy(queryParams = queryParams)
        request = Request(
          method = Method.DELETE,
          url = url,
        )
        response <- routes.runZIO(request)
        body <- response.body.asString
        expected = SuccessfulResponse("User has been deleted")
      } yield assertTrue(
        response.status == Status.Ok,
        body == expected.toJson
      )
    }.provide(
      userProgramMock(
        insertUserResponse = ZIO.unit,
        getAllUsersResponse = ZIO.succeed(Chunk.succeed(User("username", "firstname", "lastname", None))),
        deleteUserByUsernameResponse = ZIO.unit
      ),
      UserEndpoints.layer,
      ZConnectionPool.h2test
    )
  )
}
