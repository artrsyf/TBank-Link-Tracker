package linktracker.link.presenter

import linktracker.link.domain.dto

import cats.effect.IO

trait LinkPresenter[F[_]]:
    def sendSth(chatId: Long, linkUpdate: dto.LinkUpdate): F[Unit]