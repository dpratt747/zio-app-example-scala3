package http.server.endpoint

import domain.*
import domain.error.*
import domain.payload.*
import domain.response.*
import program.*
import zio.*
import zio.http.*
import zio.http.codec.*
import zio.http.endpoint.AuthType.None
import zio.http.endpoint.Endpoint
import zio.jdbc.ZConnectionPool

trait UserEndpointsAlg {
  def endpoints: List[Endpoint[Unit, ? >: CreateUserPayload & Unit & String, ? >: ZNothing <: ServiceError, ? >: SuccessfulResponse & AllUsersResponse <: Product, None]]

  def routes: Routes[ZConnectionPool, Nothing]
}

final case class UserEndpoints(
                                private val userProgram: UserProgramAlg
                              ) extends UserEndpointsAlg {

  /**
   * #1 - inserts the user provided in the request payload
   */

  private val insertUserEndpoint =
    Endpoint(Method.POST / Root / "user")
      .in[CreateUserPayload]
      .out[SuccessfulResponse](Status.Created)
      .outErrors[ServiceError](
        HttpCodec.error[ToDomainError](Status.UnprocessableEntity),
        HttpCodec.error[UsernameDuplicateError](Status.Conflict),
        HttpCodec.error[DatabaseTransactionError](Status.InternalServerError)
      )

  private val insertUserRoute = insertUserEndpoint.implement { (createUserPayload: CreateUserPayload) =>
    for {
      user <- CreateUserPayload.toDomain(createUserPayload).mapError(t => ToDomainError(t.getMessage))
      _ <- userProgram.insertUser(user)
    } yield SuccessfulResponse("success")
  }

  /**
   * #2 - gets all the users that have been stored in the database
   */

  private val getAllUsersEndpoint =
    Endpoint(Method.GET / Root / "users")
      .out[AllUsersResponse](Status.Ok)
      .outErrors[ServiceError](
        HttpCodec.error[ToDomainError](Status.InternalServerError), // failure when trying to convert UserTableRow to User domain
        HttpCodec.error[DatabaseTransactionError](Status.InternalServerError) // transaction failure result in 500
      )

  private val getAllUsersRoute = getAllUsersEndpoint.implement { _ =>
    for {
      users <- userProgram.getAllUsers
    } yield AllUsersResponse(users)
  }


  /**
   * #3 - deletes users by username
   */

  /**
   *
   * Default schemas/standardTypes can be found here: zio.schema.StandardType.scala:
   *
   * Example for Int type:
   *
   * implicit object IntType extends StandardType[Int] {
   *  override def tag: String                       = Tags.INT
   *  override def compare(x: Int, y: Int): Int      = x.compareTo(y)
   *  override val defaultValue: Either[String, Int] = Right(0)
   * }
   *
   * and then inside Schema.scala:
   *
   * implicit def primitive[A](implicit standardType: StandardType[A]): Schema[A] =
   *  Primitive(standardType, Chunk.empty)
   *
   */

  private val deleteUserEndpoint =
    Endpoint(Method.DELETE / Root / "user")
      .query(
        HttpCodec.query[String]("userName")
      )
      .out[SuccessfulResponse](Status.Ok)


  private val deleteUserRoute = deleteUserEndpoint.implement { userName =>
    for {
      _ <- userProgram.deleteUserByUsername(userName).orDie
    } yield SuccessfulResponse("User has been deleted")
  }

  /**
   * Returns the public endpoints and routes
   */
  override def endpoints: List[Endpoint[Unit, ? >: CreateUserPayload & Unit & String, ? >: ZNothing <: ServiceError, ? >: SuccessfulResponse & AllUsersResponse <: Product, None]] = List(
    insertUserEndpoint,
    getAllUsersEndpoint,
    deleteUserEndpoint
  )

  override def routes: Routes[ZConnectionPool, Nothing] = Routes.fromIterable(List(
    insertUserRoute,
    getAllUsersRoute,
    deleteUserRoute
  ))
}

object UserEndpoints {
  val layer: ZLayer[UserProgramAlg, Nothing, UserEndpointsAlg] = ZLayer.derive[UserEndpoints]
}
