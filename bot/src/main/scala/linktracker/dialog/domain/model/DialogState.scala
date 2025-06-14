package linktracker.dialog.domain.model

sealed trait DialogState

case object NoDialog extends DialogState

case class AwaitingTags(
  chatId: Long,
  url: String,
) extends DialogState

case class AwaitingFilters(
  chatId: Long,
  url: String,
  tags: List[String],
) extends DialogState
