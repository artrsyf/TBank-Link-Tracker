package linktracker.link.cache.Redis

import cats.effect.Sync
import cats.syntax.all._
import dev.profunktor.redis4cats.RedisCommands
import org.typelevel.log4cats.Logger
import tethys._
import tethys.jackson._

import linktracker.link.domain.dto.LinkResponse
import linktracker.link.cache.LinkCache

final class RedisLinkCache[F[_]: Sync](
  redis: RedisCommands[F, String, String],
  logger: Logger[F]
) extends LinkCache[F]:
  private def key(chatId: Long): String = s"links:$chatId"

  override def get(chatId: Long): F[List[LinkResponse]] =
    redis.get(key(chatId)).flatMap {
      case Some(json) =>
        json.jsonAs[List[LinkResponse]] match {
          case Right(links) =>
            logger.info(s"Cache hit | chatId=$chatId") *> Sync[F].pure(links)
          case Left(err) =>
            logger.warn(s"Failed to parse cache | chatId=$chatId | error=${err.getMessage}") *>
              redis.del(key(chatId)) *>
              Sync[F].pure(List.empty)
        }
      case None =>
        logger.debug(s"Cache miss | chatId=$chatId") *> Sync[F].pure(List.empty)
    }

  override def set(links: List[LinkResponse], chatId: Long): F[Unit] =
    val json = links.asJson
    redis.set(key(chatId), json)

  override def delete(chatId: Long): F[Unit] =
    redis.del(key(chatId)).void
