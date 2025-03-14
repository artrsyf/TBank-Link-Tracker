package linkscrapper.Chat.domain.entity

sealed trait ChatError extends Throwable:
    def message: String

object ChatError:
    case object ErrAlreadyRegistered extends ChatError:
        val message = "Чат уже зарегистрирован"

    case object ErrDeletionInvalidChat extends ChatError:
        val message = "Ошибка удаления чата"