import java.util.NoSuchElementException

import akka.actor._
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration._
import akka.util.Timeout

case class GetAccountRequest(accountId: String)

case class CreateAccountRequest(initialBalance: Double)

case class IdentifyActor()

class Bank(val bankId: String) extends Actor {

    val accountCounter = new AtomicInteger(1000)

    def createAccount(initialBalance: Double): ActorRef = {
        // Create a new Account Actor and return its actor reference. Accounts should be assigned with unique ids (increment with 1).
        val accountId = accountCounter.incrementAndGet().toString
        BankManager.createAccount(accountId, bankId, initialBalance)
    }

    def findAccount(accountId: String): Option[ActorRef] = {
        // Use BankManager to look up an account with ID accountId
        Some(BankManager.findAccount(this.bankId, accountId))
    }

    def findOtherBank(bankId: String): Option[ActorRef] = {
        // Use BankManager to look up a different bank with ID bankId
        Some(BankManager.findBank(bankId))
    }

    override def receive = {
        case CreateAccountRequest(initialBalance) => sender ! this.createAccount(initialBalance) // Create a new account
        case GetAccountRequest(id) => sender ! this.findAccount(id) // Return account
        case IdentifyActor => sender ! this
        case t: Transaction => processTransaction(t)

        case t: TransactionRequestReceipt => {
            // Forward receipt
            if (this.findAccount(t.toAccountNumber).isDefined) {
                this.findAccount(t.toAccountNumber).get ! t
            }

        }
            case msg => sender ! msg
    }

    def processTransaction(t: Transaction): Unit = {
        implicit val timeout = new Timeout(5 seconds)
        val isInternal = t.to.length <= 4
        val toBankId = if (isInternal) bankId else t.to.substring(0, 4)
        val toAccountId = if (isInternal) t.to else t.to.substring(4)
        val transactionStatus = t.status
        
        // Forward Transaction t to an account or another bank, depending on the "to"-address.

        try {

            if (isInternal || toBankId == this.bankId) {
            this.findAccount(toAccountId).get ! t
            } else {
            this.findOtherBank(toBankId).get ! t
            }
        }
        catch {
            case _:NoSuchElementException => t.status = TransactionStatus.FAILED
            sender ! new TransactionRequestReceipt(t.from, t.id, t)
        }
    }
}
