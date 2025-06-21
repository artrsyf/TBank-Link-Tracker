package linktracker.link.usecase

import cats.effect.IO
import cats.syntax.traverse._

import linktracker.link.domain.dto
import linktracker.link.domain.dto.LinkUpdate
import linktracker.link.presenter.LinkPresenter

trait LinkUsecase[F[_]]:
  def serveLinks(linkUpdates: List[dto.LinkUpdate]): F[Unit]

object LinkUsecase:
  final private class Impl(
    linkPresenter: LinkPresenter[IO],
  ) extends LinkUsecase[IO]:
    override def serveLinks(linkUpdates: List[LinkUpdate]): IO[Unit] =
      for {
        _ <- linkUpdates.traverse { linkUpdate =>
          linkUpdate.tgChatIds.traverse { chatId =>
            linkPresenter.publishLinkUpdate(chatId, linkUpdate)
          }
        }
      } yield ()

  def make(linkPresenter: LinkPresenter[IO]): LinkUsecase[IO] =
    Impl(linkPresenter)
