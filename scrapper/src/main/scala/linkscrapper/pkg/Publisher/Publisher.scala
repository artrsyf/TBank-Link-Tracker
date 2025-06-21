package linkscrapper.pkg.Publisher

import linkscrapper.link.domain.dto

trait LinkPublisher[F[_]]:
  def publishLinks(linkUpdates: List[dto.LinkUpdate]): F[Unit]
