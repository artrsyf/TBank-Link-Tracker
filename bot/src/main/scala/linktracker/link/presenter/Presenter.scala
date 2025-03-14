package linktracker.link.presenter

import cats.effect.IO

import linktracker.link.domain.dto

trait LinkPresenter[F[_]]:
    def publishLinkUpdate(chatId: Long, linkUpdate: dto.LinkUpdate): F[Unit]