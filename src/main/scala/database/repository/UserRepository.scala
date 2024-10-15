package database.repository

import database.schema.UserTableRow
import domain.User
import zio.jdbc.{ZConnection, sqlInterpolator}
import zio.{Chunk, URIO, ZIO, ZLayer}

trait UserRepositoryAlg {
  def insertUser(user: User): URIO[ZConnection, Long]

  def getAllUsers: URIO[ZConnection, Chunk[UserTableRow]]

  def deleteUserByUsername(userName: String): URIO[ZConnection, Long]
}

final case class UserRepository() extends UserRepositoryAlg {
  override def insertUser(user: User): URIO[ZConnection, Long] = ZIO.logInfo("Inserting into user_table") *>
    sql"insert into user_table (user_name, first_name, last_name, address)"
      .values((user.userName, user.firstName, user.lastName, user.address))
      .insert
      .orDie

  override def getAllUsers: URIO[ZConnection, Chunk[UserTableRow]] = ZIO.logInfo("Retrieving all users from the user_table") *>
    sql"select * from user_table".query[UserTableRow].selectAll.orDie

  override def deleteUserByUsername(userName: String): URIO[ZConnection, Long] =
    ZIO.logInfo(s"Deleting user by $userName") *> sql"delete from user_table where user_name=$userName"
      .delete
      .orDie
}

object UserRepository {
  val layer: ZLayer[Any, Nothing, UserRepositoryAlg] = ZLayer.derive[UserRepository]
}
