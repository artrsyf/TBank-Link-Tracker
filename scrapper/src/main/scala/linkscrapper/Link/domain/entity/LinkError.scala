package linkscrapper.link.domain.entity

sealed trait LinkError extends Throwable:
  def message: String

object LinkError:
  case object ErrInvalidUrl extends LinkError:
    override def message = "URL не соответствует поддерживаемым клиентам"

  case object ErrNoSuchLink extends LinkError:
    override def message = s"Ссылка с данным URL не найдена"
