package database.service

import database.repository.*
import zio.*
import zio.jdbc.{ZConnectionPool, transaction}

trait StatusServiceAlg {
  def isDBLive: ZIO[ZConnectionPool, Throwable, Boolean]
}

final case class StatusService(
                                private val statusRepository: StatusRepositoryAlg
                              ) extends StatusServiceAlg {

  override def isDBLive: ZIO[ZConnectionPool, Throwable, Boolean] = transaction(
    statusRepository.select1().map(_.isDefined)
  )

}

object StatusService {
  val layer: ZLayer[StatusRepositoryAlg, Nothing, StatusServiceAlg] = ZLayer.derive[StatusService]
}