package configuration

import zio.*
import zio.config.typesafe.TypesafeConfigProvider
import zio.test.*
import zio.test.Assertion.*

object ApplicationConfigSpec extends ZIOSpecDefault {
  override def spec: Spec[Any, Any] =
    suite("ApplicationConfig")(
      test("can load successfully") {
        (for {
          configAlg <- ZIO.service[ApplicationConfigAlg]
          hoconConfig <- configAlg.hoconConfig
          config = hoconConfig.db
        } yield assertTrue(
          config.exposedPort == 5432,
          config.host == "localhost",
          config.jdbcUrl == "jdbc:postgresql://localhost:5432/zio-app-example",
          config.user == "postgres",
          config.password == "postgres",
          config.database == "zio-app-example"
        ))
          .provide(
            ApplicationConfig.layer,
            ZLayer.succeed(TypesafeConfigProvider.fromResourcePath())
          )
      },
      test("can load successfully with provided hocon string") {
        val validConfig = TypesafeConfigProvider
          .fromHoconString(
            s"""
               |db {
               |    exposed-port = 10
               |    host = "localhost"
               |    jdbc-url = "some_url"
               |    user = "user"
               |    password = "password"
               |    database = "some_db"
               |}
               |""".stripMargin
          )

        (for {
          configAlg <- ZIO.service[ApplicationConfigAlg]
          hoconConfig <- configAlg.hoconConfig
          config = hoconConfig.db
        } yield assertTrue(
          config.exposedPort == 10,
          config.host == "localhost",
          config.jdbcUrl == "some_url",
          config.user == "user",
          config.password == "password",
          config.database == "some_db"
        ))
          .provide(
            ApplicationConfig.layer,
            ZLayer.succeed(validConfig)
          )
      }
    )
}
