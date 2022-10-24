package EShop.lab2

import akka.actor.Cancellable
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

import scala.language.postfixOps
import scala.concurrent.duration._
import EShop.lab3.{OrderManager, Payment}

object TypedCheckout {

  sealed trait Data
  case object Uninitialized                               extends Data
  case class SelectingDeliveryStarted(timer: Cancellable) extends Data
  case class ProcessingPaymentStarted(timer: Cancellable) extends Data

  sealed trait Command
  case object StartCheckout                                                                  extends Command
  case class SelectDeliveryMethod(method: String)                                            extends Command
  case object CancelCheckout                                                                 extends Command
  case object ExpireCheckout                                                                 extends Command
  case class SelectPayment(payment: String, orderManagerRef: ActorRef[Event]) extends Command
  case object ExpirePayment                                                                  extends Command
  case object ConfirmPaymentReceived                                                         extends Command

  sealed trait Event
  case object CheckOutClosed                           extends Event
  case class PaymentStarted(paymentRef: ActorRef[Any]) extends Event

  def apply(cartActor: ActorRef[TypedCartActor.Command]): Behavior[TypedCheckout.Command] = Behaviors.setup(
    _ => {
      new TypedCheckout(cartActor).start
    }
  )
}

class TypedCheckout(
  cartActor: ActorRef[TypedCartActor.Command]
) {
  import TypedCheckout._

  val checkoutTimerDuration: FiniteDuration = 1 seconds
  val paymentTimerDuration: FiniteDuration  = 1 seconds

  var orderManagerRef: ActorRef[OrderManager.Command] = _

  def start: Behavior[TypedCheckout.Command] = Behaviors.receive((context, msg) =>
    msg match {
      case StartCheckout =>
        selectingDelivery(context.scheduleOnce(checkoutTimerDuration, context.self, ExpireCheckout))
    }
  )

  def selectingDelivery(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receiveMessage {
    case CancelCheckout =>
      timer.cancel()
      cancelled
    case ExpireCheckout =>
      cancelled
    case SelectDeliveryMethod(_) =>
      selectingPaymentMethod(timer)
  }

  def selectingPaymentMethod(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receive((context, msg) =>
    msg match {
      case CancelCheckout =>
        timer.cancel()
        cancelled
      case ExpireCheckout =>
        cancelled
      case SelectPayment(payment: String, orderManagerRef: ActorRef[Any]) =>
        timer.cancel()
        this.orderManagerRef = orderManagerRef
        val paymentRef = context.spawn(Payment(payment, orderManagerRef, context.self), "payment")
        orderManagerRef ! OrderManager.ConfirmPaymentStarted(paymentRef)
        processingPayment(context.scheduleOnce(paymentTimerDuration, context.self, ExpirePayment))
    }
  )
  def processingPayment(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receiveMessage {
    case CancelCheckout =>
      timer.cancel()
      cancelled
    case ExpirePayment =>
      cancelled
    case ConfirmPaymentReceived =>
      timer.cancel()
      this.orderManagerRef ! OrderManager.ConfirmPaymentReceived
      cartActor ! TypedCartActor.ConfirmCheckoutClosed
      closed
  }

  def cancelled: Behavior[TypedCheckout.Command] = Behaviors.receiveMessage(_ => Behaviors.stopped)

  def closed: Behavior[TypedCheckout.Command] = Behaviors.receiveMessage(_ => Behaviors.stopped)
}
