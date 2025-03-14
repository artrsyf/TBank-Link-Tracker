package linkscrapper.Chat.domain.entity

sealed trait ChatError extends Throwable:
    def message: String

object ChatError:
    case object ErrAlreadyRegistered extends ChatError:
        override def message = "Чат уже зарегистрирован"

    case object ErrDeletionInvalidChat extends ChatError:
        override def message = "Ошибка удаления чата"