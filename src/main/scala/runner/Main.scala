package runner

import _root_.http.server.endpoint.*
import configuration.{ApplicationConfig, ApplicationConfigAlg}
import database.migration.FlywayResource
import database.repository.{StatusRepository, UserRepository}
import database.service.{StatusService, UserService}
import database.util.ZConnectionPoolWrapper
import program.{HealthProgram, UserProgram}
import zio.*
import zio.config.typesafe.TypesafeConfigProvider
import zio.http.*
import zio.http.codec.PathCodec
import zio.http.codec.PathCodec.*
import zio.http.endpoint.openapi.*
import zio.jdbc.{ZConnectionPool, ZConnectionPoolConfig}

object Main extends ZIOAppDefault {
  
  private val logLevel: Status => LogLevel = (incomingStatus: Status) =>
    if (incomingStatus.isSuccess) LogLevel.Info
    else LogLevel.Error

  override def run: ZIO[Any, Throwable, Unit] =
    ZIO.serviceWithZIO[ApplicationConfigAlg](_.hoconConfig).flatMap { (appConfig: ApplicationConfig.HoconConfig) =>
      (for {
        _ <- FlywayResource.flywayResource(appConfig.db.jdbcUrl, appConfig.db.user, appConfig.db.password) // run migrations
        (healthEndpoints, healthRoutes) <- ZIO.serviceWith[HealthCheckEndpointsAlg] { service => (service.endpoints, service.routes) }
        (userEndpoints, userRoutes) <- ZIO.serviceWith[UserEndpointsAlg] { service => (service.endpoints, service.routes) }
        loggingMiddleware = Middleware.requestLogging(
          logRequestBody = true,
          logResponseBody = true,
          level = logLevel
        )
        logAnnotationMiddleware = Middleware.logAnnotate(request =>
          Set(
            zio.LogAnnotation("method", request.method.name),
            zio.LogAnnotation("url", request.url.encode)
          )
        )
        composedMiddleware = logAnnotationMiddleware ++ loggingMiddleware //todo: add new middleware here
        composedEndpoints = healthEndpoints ++ userEndpoints // todo: add new endpoints here
        openAPI = OpenAPIGen.fromEndpoints(title = "ZIO application example", version = "1.0", composedEndpoints)
        swaggerRoute = SwaggerUI.routes("docs" / "openapi", openAPI)
        composedRoutesWithLogging = (healthRoutes ++ userRoutes ++ swaggerRoute) @@ composedMiddleware //todo: add new routes here
        _ <- Server.serve(composedRoutesWithLogging)
      } yield ()).provide(
        Scope.default,
        Server.default,
        ZLayer.succeed(ZConnectionPoolConfig.default),
        ZConnectionPoolWrapper.connectionPool(
          host = appConfig.db.host,
          port = appConfig.db.exposedPort,
          database = appConfig.db.database,
          user = appConfig.db.user,
          password = appConfig.db.password
        ),
        StatusRepository.layer,
        UserRepository.layer,
        StatusService.layer,
        UserService.layer,
        HealthProgram.layer,
        UserProgram.layer,
        HealthCheckEndpoints.layer,
        UserEndpoints.layer
      )
    }.provide(
      ZLayer.succeed(TypesafeConfigProvider.fromResourcePath()),
      ApplicationConfig.layer
  )
}
