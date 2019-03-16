import akka.actor._
import exceptions._
import scala.collection.immutable.HashMap

case class TransactionRequest(toAccountNumber: String, amount: Double)

case class TransactionRequestReceipt(toAccountNumber: String,
                                     transactionId: String,
                                     transaction: Transaction)

case class BalanceRequest()

class Account(val accountId: String, val bankId: String, val initialBalance: Double = 0) extends Actor {

    private var transactions = HashMap[String, Transaction]()

    class Balance(var amount: Double) {}

    val balance = new Balance(initialBalance)

    def getFullAddress: String = {
        bankId + accountId
    }

    def getTransactions: List[Transaction] = {
        // Return a list of all Transaction-objects stored in transactions
        this.transactions.values.toList
    }

    def allTransactionsCompleted: Boolean = {
        // Return whether all Transaction-objects in transactions are completed
        this.transactions foreach {case (key, transaction) =>
            if (transaction.status != TransactionStatus.SUCCESS) {
                false
            }
        }
        true
    }

    def withdraw(amount: Double): Unit = {
    if (amount < 0) {
      throw new IllegalAmountException
    }
    else if (amount > balance.amount) {
      throw new NoSufficientFundsException
    }
    else {
      this.synchronized {
        balance.amount = balance.amount - amount
      }
    }
  }

  def deposit(amount: Double): Unit = {
    if (amount < 0) {
      throw new IllegalAmountException
    }
    else {
      this.synchronized {
        balance.amount = balance.amount + amount
      }
    }
  }

  def getBalanceAmount: Double = balance.amount

    def sendTransactionToBank(t: Transaction): Unit = {
        // Send a message containing t to the bank of this account
        BankManager.findBank(t.from.substring(0, 4)) ! t
    }

    def transferTo(accountNumber: String, amount: Double): Transaction = {

        val t = new Transaction(from = getFullAddress, to = accountNumber, amount = amount)

        if (reserveTransaction(t)) {
            try {
                withdraw(amount)
                sendTransactionToBank(t)

            } catch {
                case _: NoSufficientFundsException | _: IllegalAmountException =>
                    t.status = TransactionStatus.FAILED
            }
        }
        t
    }

    def reserveTransaction(t: Transaction): Boolean = {
      if (!transactions.contains(t.id)) {
        transactions += (t.id -> t)
        return true
      }
      false
    }

    override def receive = {
      case IdentifyActor => sender ! this

      case TransactionRequestReceipt(to, transactionId, transaction) => {
        // Process receipt
        var trans = transactions.get(transactionId).get
        if (transactions.contains(transactionId) && to.equals(this.getFullAddress)) {
          trans.status = transaction.status
          trans.receiptReceived = true
        } else {
          trans.receiptReceived = true
          trans.status = TransactionStatus.FAILED
        }
      }

      case BalanceRequest => sender ! this.balance.amount

      case t: Transaction => {
        // Handle incoming transaction
        this.deposit(t.amount)
        t.status = TransactionStatus.SUCCESS
        sender ! new TransactionRequestReceipt(t.from, t.id, t)
      }

      case msg => sender ! msg
  }
}
