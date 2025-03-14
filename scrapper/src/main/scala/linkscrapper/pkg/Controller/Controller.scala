package linkscrapper.pkg.Controller

import sttp.tapir.server.ServerEndpoint

trait Controller[F[_]]:
  def endpoints: List[ServerEndpoint[Any, F]]
