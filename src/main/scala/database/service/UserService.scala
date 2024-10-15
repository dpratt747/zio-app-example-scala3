package database.service

import database.repository.UserRepositoryAlg
import database.schema.UserTableRow
import domain.User
import domain.error.*
import org.postgresql.util.{PSQLException, PSQLState}
import zio.*
import zio.jdbc.{ZConnectionPool, transaction}

trait UserServiceAlg {
  def insertUser(user: User): ZIO[ZConnectionPool, ServiceError, Unit]

  def getAllUsers: ZIO[ZConnectionPool, ServiceError, Chunk[User]]

  def deleteUserByUsername(userName: String): ZIO[ZConnectionPool, ServiceError, Unit]
}

final case class UserService(
                              private val userRepository: UserRepositoryAlg
                            ) extends UserServiceAlg {

  override def insertUser(user: User): ZIO[ZConnectionPool, ServiceError, Unit] = transaction(
    userRepository.insertUser(user).unit
  ).mapErrorCause { cause =>
    cause.squash match {
      case e: PSQLException if e.getSQLState == PSQLState.UNIQUE_VIOLATION.getState =>
        Cause.fail(UsernameDuplicateError(e.getMessage))
      case e =>
        Cause.fail(DatabaseTransactionError(e.getMessage))
    }
  }

  override def getAllUsers: ZIO[ZConnectionPool, ServiceError, Chunk[User]] = transaction(
    for {
      userTableChunk <- userRepository.getAllUsers
      userChunk <- ZIO.foreachPar(
        userTableChunk.map(userTableRow =>
          UserTableRow.toDomain(userTableRow).mapError(t => ToDomainError(t.getMessage))
        ))(identity)
    } yield userChunk
  ).mapErrorCause { cause =>
    Cause.fail(DatabaseTransactionError(cause.squash.getMessage))
  }

  override def deleteUserByUsername(userName: String): ZIO[ZConnectionPool, ServiceError, Unit] = transaction(
    userRepository.deleteUserByUsername(userName).unit
  ).mapErrorCause { cause =>
    Cause.fail(DatabaseTransactionError(cause.squash.getMessage))
  }

}

object UserService {
  val layer: ZLayer[UserRepositoryAlg, Nothing, UserServiceAlg] = ZLayer.derive[UserService]
}