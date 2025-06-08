package linkscrapper.dialog.repository.InMemory

import cats.effect.IO
import scala.collection.concurrent.TrieMap

import linktracker.dialog.domain.model.DialogState
import linktracker.dialog.repository.DialogRepository

class InMemoryDialogRepository extends DialogRepository[IO] {
  private val dialogStateMap = new TrieMap[Long, DialogState]()

  override def get(chatId: Long): IO[Option[DialogState]] = IO.delay {
    dialogStateMap.get(chatId)
  }

  override def put(chatId: Long, state: DialogState): IO[Unit] = IO.delay {
    dialogStateMap.put(chatId, state)
  }

  override def delete(chatId: Long): IO[Unit] = IO.delay {
    dialogStateMap.remove(chatId)
  }
}
