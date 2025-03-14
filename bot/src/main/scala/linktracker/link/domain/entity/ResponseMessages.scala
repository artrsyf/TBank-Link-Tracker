package linktracker.link.domain.entity

import linktracker.link.domain.dto

sealed trait ResponseMessage extends Throwable:
    def message: String

object ResponseMessage:
    case object SuccessfullRegisterMessage extends ResponseMessage:
        override def message: String = 
            "✅ Регистрация прошла успешно!"

    case object FailedRegisterMessage extends ResponseMessage:
        override def message: String = 
            "⚠ Ошибка регистрации!"

    case object ServerUnavailableMessage extends ResponseMessage:
        override def message: String = 
            "❌ Сервер недоступен. Попробуйте позже."

    case class ErrorMessage(errorMessage: String) extends ResponseMessage:
        override def message: String = 
            s"❌ Произошла ошибка: ${errorMessage}"

    case object MissingUrlArgMessage extends ResponseMessage:
        override def message: String = 
            "⚠ Введите URL!"

    case object SuccessfullLinkAdditionMessage extends ResponseMessage:
        override def message: String = 
            "✅ URL успешно добавлен в трекинг!"

    case object FailedLinkAdditionMessage extends ResponseMessage:
        override def message: String = 
            "⚠ Ошибка при добавлении URL!"

    case object SuccessfullLinkDeletionMessage extends ResponseMessage:
        override def message: String = 
            "✅ URL успешно удален из трекинга!"

    case object FailedLinkDeltionMessage extends ResponseMessage:
        override def message: String = 
            "⚠ Ошибка при удалении URL!"

    case class IncomingLinkUpdateMessage(linkUpdate: dto.LinkUpdate) extends ResponseMessage:
        override def message: String = 
            s"🔔 Обновление для ссылки: ${linkUpdate.url}\n${linkUpdate.description}"

    case class FailedParseJsonMessage(errorMessage: String) extends ResponseMessage:
        override def message: String = 
           s"Ошибка при обработке данных: $errorMessage"

    case object EmptyLinkListMessage extends ResponseMessage:
        override def message: String = 
           "Список отслеживаемых ссылок пуст!"

    case class LinkRepresentationMessage(link: dto.LinkResponse) extends ResponseMessage:
        override def message: String = 
           s"""|ID: ${link.id}\n
               |URL: ${link.url}\n
               |Tags: ${link.tags.mkString(", ")}\n
               |Filters: ${link.filters.mkString(", ")}""".stripMargin

    case class RequestErrorMessage(errorMessage: String) extends ResponseMessage:
        override def message: String = 
           s"Ошибка при запросе: $errorMessage"
    
    case object UnknownCommandMessage extends ResponseMessage:
        override def message: String = 
            "⚠ Неизвестная команда!"
    
    case object NotCommandMessage extends ResponseMessage:
        override def message: String = 
            "⚠ Команда не введена!"

    case class HelpReferenceMessage(commandsText: String) extends ResponseMessage:
        override def message: String = 
            s"ℹ️ Справка по командам:\n$commandsText"