package com.verschraenkt.ci.engine.core

import com.verschraenkt.ci.engine.api.Snowflake
import java.net.InetAddress
import scala.util.Try

enum AppEnv:
  case Development, Test, Production

object AppEnv:
  def fromString(s: String): Option[AppEnv] =
    s.trim.toLowerCase match
      case "dev" | "development" => Some(AppEnv.Development)
      case "test"                => Some(AppEnv.Test)
      case "prod" | "production" => Some(AppEnv.Production)
      case _                     => None

final case class SnowflakeConfig(
    machineId: Int,
    maxClockBackwardMs: Long = 5L
)

final case class AppConfig(
    env: AppEnv,
    snowflake: SnowflakeConfig
)

object AppConfig:
  lazy val current: AppConfig =
    load().fold(msg => throw new IllegalArgumentException(msg), identity)

  def machineId: Int = current.snowflake.machineId

  private def load(): Either[String, AppConfig] =
    val env = read("APP_ENV").flatMap(AppEnv.fromString).getOrElse(AppEnv.Development)

    val machineIdEither =
      readInt("CI_SNOWFLAKE_MACHINE_ID")
        .orElse(readIntProp("ci.snowflake.machineId"))
        .orElse {
          // Dev/Test fallback only; never implicit in production
          if env == AppEnv.Production then None else Some(stableDevMachineId())
        }
        .toRight("Missing machine id. Set CI_SNOWFLAKE_MACHINE_ID (0-1023).")

    val backwardMsEither =
      readLong("CI_SNOWFLAKE_MAX_CLOCK_BACKWARD_MS")
        .orElse(readLongProp("ci.snowflake.maxClockBackwardMs"))
        .getOrElse(5L) match
        case x if x >= 0L => Right(x)
        case x            => Left(s"maxClockBackwardMs must be >= 0, got $x")

    for
      machineId <- machineIdEither
      _ <- Either.cond(
        machineId >= 0 && machineId <= Snowflake.MaxMachineId,
        (),
        s"machineId must be 0-${Snowflake.MaxMachineId}, got $machineId"
      )
      maxBackward <- backwardMsEither
    yield AppConfig(
      env = env,
      snowflake = SnowflakeConfig(machineId = machineId, maxClockBackwardMs = maxBackward)
    )

  private def stableDevMachineId(): Int =
    val host = Try(InetAddress.getLocalHost.getHostName).getOrElse("unknown-host")
    Math.floorMod(host.hashCode, Snowflake.MaxMachineId + 1)

  private def read(key: String): Option[String] =
    Option(System.getenv(key)).map(_.trim).filter(_.nonEmpty)

  private def readInt(key: String): Option[Int] =
    read(key).flatMap(v => Try(v.toInt).toOption)

  private def readLong(key: String): Option[Long] =
    read(key).flatMap(v => Try(v.toLong).toOption)

  private def readIntProp(key: String): Option[Int] =
    Option(System.getProperty(key)).map(_.trim).filter(_.nonEmpty).flatMap(v => Try(v.toInt).toOption)

  private def readLongProp(key: String): Option[Long] =
    Option(System.getProperty(key)).map(_.trim).filter(_.nonEmpty).flatMap(v => Try(v.toLong).toOption)
