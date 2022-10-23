package EShop.lab2

import akka.actor.{Actor, ActorRef, ActorSystem, Cancellable, Props}
import akka.event.{Logging, LoggingReceive}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

object CartActor {

  sealed trait Command
  case class AddItem(item: Any)        extends Command
  case class RemoveItem(item: Any)     extends Command
  case object ExpireCart               extends Command
  case object StartCheckout            extends Command
  case object ConfirmCheckoutCancelled extends Command
  case object ConfirmCheckoutClosed    extends Command

  sealed trait Event
  case class CheckoutStarted(checkoutRef: ActorRef) extends Event

  def props: Props = Props(new CartActor())
}

class CartActor extends Actor {

  import CartActor._

  private val log       = Logging(context.system, this)
  val cartTimerDuration: FiniteDuration = 5 seconds

  private def scheduleTimer: Cancellable = context.system.scheduler.scheduleOnce(
    cartTimerDuration, self, ExpireCart
  )

  def receive: Receive = LoggingReceive {
    case AddItem(item) =>
      context become nonEmpty(Cart.empty.addItem(item), scheduleTimer)
  }

  def empty: Receive = LoggingReceive {
    case AddItem(item) =>
      context become nonEmpty(Cart.empty.addItem(item), scheduleTimer)
  }

  def nonEmpty(cart: Cart, timer: Cancellable): Receive = LoggingReceive {
    case AddItem(item) =>
      timer.cancel()
      context become nonEmpty(cart.addItem(item), scheduleTimer)
    case RemoveItem(item) =>
      if (cart.contains(item)) {
        val newCart = cart.removeItem(item)
        if (newCart.size == 0 ){
          timer.cancel()
          context become empty
        } else {
          timer.cancel()
          context become nonEmpty(newCart, scheduleTimer)
        }
      }
    case ExpireCart =>
      timer.cancel()
      context become empty
    case StartCheckout =>
      timer.cancel()
      context become inCheckout(cart)
  }

  def inCheckout(cart: Cart): Receive = LoggingReceive {
    case ConfirmCheckoutCancelled =>
      context become nonEmpty(cart, scheduleTimer)
    case ConfirmCheckoutClosed =>
      context become empty
  }

}

object CartActorApp extends App {
  val actorSystem = ActorSystem("cartSystem")
  val cartActor   = actorSystem.actorOf(Props[CartActor], "cartActor")

  import CartActor._

  cartActor ! AddItem("Item1")
  cartActor ! AddItem("Item2")
  cartActor ! RemoveItem("Item2")
  cartActor ! StartCheckout
  cartActor ! ConfirmCheckoutClosed
  cartActor ! AddItem("Item3")
  cartActor ! ExpireCart

  Thread.sleep(1000)
  sys.exit()
}