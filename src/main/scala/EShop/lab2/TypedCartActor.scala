package EShop.lab2

import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}

import scala.language.postfixOps
import scala.concurrent.duration._
import EShop.lab3.OrderManager

object TypedCartActor {
  def apply(): Behavior[TypedCartActor.Command] = Behaviors.setup(
    _ => {
      new TypedCartActor().start
    }
  )

  sealed trait Command
  case class AddItem(item: Any)                                             extends Command
  case class RemoveItem(item: Any)                                          extends Command
  case object ExpireCart                                                    extends Command
  case class StartCheckout(orderManagerRef: ActorRef[OrderManager.Command]) extends Command
  case object ConfirmCheckoutCancelled                                      extends Command
  case object ConfirmCheckoutClosed                                         extends Command
  case class GetItems(sender: ActorRef[Cart])                               extends Command // command made to make testing easier

  sealed trait Event
  case class CheckoutStarted(checkoutRef: ActorRef[TypedCheckout.Command]) extends Event
}

class TypedCartActor {

  import TypedCartActor._

  val cartTimerDuration: FiniteDuration = 5 seconds

  private def scheduleTimer(context: ActorContext[TypedCartActor.Command]): Cancellable =
    context.scheduleOnce(cartTimerDuration, context.self, ExpireCart)

  def start: Behavior[TypedCartActor.Command] = empty

  def empty: Behavior[TypedCartActor.Command] = Behaviors.receive((context, msg) =>
    msg match {
      case AddItem(item) =>
        nonEmpty(Cart.empty.addItem(item), scheduleTimer(context))
      case GetItems(sender) =>
        sender ! Cart.empty
        Behaviors.same
    }
  )

  def nonEmpty(cart: Cart, timer: Cancellable): Behavior[TypedCartActor.Command] = Behaviors.receive((context, msg) =>
    msg match {
      case AddItem(item) =>
        timer.cancel()
        nonEmpty(cart.addItem(item), scheduleTimer(context))
      case GetItems(sender) =>
        sender ! cart
        Behaviors.same
      case RemoveItem(item) =>
        if (cart.contains(item)) {
          val newCart = cart.removeItem(item)
          timer.cancel()
          if (newCart.size == 0) {
            empty
          } else {
              nonEmpty(newCart, scheduleTimer(context))
          }
        } else {
          Behaviors.same
        }
      case ExpireCart =>
        timer.cancel()
        empty
      case StartCheckout(orderManagerRef: ActorRef[OrderManager.Command]) =>
        timer.cancel()
        val checkout = context.spawn(TypedCheckout(context.self), "checkout")
        orderManagerRef ! OrderManager.ConfirmCheckoutStarted(checkout)
        inCheckout(cart)
    }
  )

  def inCheckout(cart: Cart): Behavior[TypedCartActor.Command] = Behaviors.receive((context, msg) =>
    msg match {
      case ConfirmCheckoutCancelled =>
        nonEmpty(cart, scheduleTimer(context))
      case ConfirmCheckoutClosed =>
        empty
    }
  )
}
