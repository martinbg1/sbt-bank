

class TransactionQueue {

  private var elements: List[Transaction] = Nil

  // Remove and return the first element from the queue
  def pop: Transaction = {
    this.synchronized {
      val currentTop = peek
      elements = elements.tail
      currentTop
    }
  }

  // Return the first element from the queue without removing it
  def peek: Transaction = elements.head

  // Return whether the queue is empty
  def isEmpty: Boolean = elements.isEmpty

  // Add new element to the back of the queue
  def push(t: Transaction): Unit = {
    this.synchronized {
      elements = t :: elements
    }
  }

  // Return an iterator to allow you to iterate over the queue
  def iterator: Iterator[Transaction] = elements.iterator

}

class Transaction(val transactionsQueue: TransactionQueue,
                  val processedTransactions: TransactionQueue,
                  val from: Account,
                  val to: Account,
                  val amount: Double,
                  val allowedAttemps: Integer) extends Runnable {

  var status: TransactionStatus.Value = TransactionStatus.PENDING

  override def run: Unit = {

    def doTransaction() = {
      from withdraw amount
      to deposit amount
    }

    if (allowedAttemps == 0) {
      status = TransactionStatus.FAILED
      processedTransactions.push(this)
    } else {
      try {
        if (from.uid < to.uid) from synchronized {
          to synchronized {
            doTransaction()
          }
        } else to synchronized {
          from synchronized {
            doTransaction()
          }
        }
        status = TransactionStatus.SUCCESS
        processedTransactions.push(this)
      } catch {
        case _: Throwable => transactionsQueue.push(new Transaction(transactionsQueue, processedTransactions, from, to, amount, allowedAttemps - 1))
      }
    }
  }
}

object TransactionStatus extends Enumeration {
  val SUCCESS, PENDING, FAILED = Value
}
