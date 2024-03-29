package EShop.lab3

import EShop.lab2.{TypedCartActor, TypedCheckout}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

object OrderManager {

  sealed trait Command
  case class AddItem(id: String, sender: ActorRef[Ack])                                               extends Command
  case class RemoveItem(id: String, sender: ActorRef[Ack])                                            extends Command
  case class SelectDeliveryAndPaymentMethod(delivery: String, payment: String, sender: ActorRef[Ack]) extends Command
  case class Buy(sender: ActorRef[Ack])                                                               extends Command
  case class Pay(sender: ActorRef[Ack])                                                               extends Command
  case class ConfirmCheckoutStarted(checkoutRef: ActorRef[TypedCheckout.Command])                     extends Command
  case class ConfirmPaymentStarted(paymentRef: ActorRef[Payment.Command])                             extends Command
  case object ConfirmPaymentReceived                                                                  extends Command
  case object PaymentRejected                                                                         extends Command
  case object PaymentRestarted                                                                        extends Command

  sealed trait Ack
  case object Done extends Ack //trivial ACK
}

class OrderManager {

  import OrderManager._

  def start: Behavior[OrderManager.Command] = Behaviors.setup { context =>
    val cartActor = context.spawn(TypedCartActor(), "cartActor")
    open(cartActor)
  }

  def uninitialized: Behavior[OrderManager.Command] = start

  def open(cartActor: ActorRef[TypedCartActor.Command]): Behavior[OrderManager.Command] = Behaviors.receiveMessage {
    case AddItem(id, sender) =>
      cartActor ! TypedCartActor.AddItem(id)
      sender ! Done
      Behaviors.same
    case RemoveItem(id, sender) =>
      cartActor ! TypedCartActor.RemoveItem(id)
      sender ! Done
      Behaviors.same
    case Buy(sender) =>
      inCheckout(cartActor, sender)
    case _ =>
      Behaviors.same
  }

  def inCheckout(
    cartActorRef: ActorRef[TypedCartActor.Command],
    senderRef: ActorRef[Ack]
  ): Behavior[OrderManager.Command] =
    Behaviors.setup { context =>
      cartActorRef ! TypedCartActor.StartCheckout(context.self)
      Behaviors.receiveMessage {
        case ConfirmCheckoutStarted(checkoutRef) =>
          senderRef ! Done
          inCheckout(checkoutRef)
        case _ =>
          Behaviors.same
      }
    }

  def inCheckout(checkoutActorRef: ActorRef[TypedCheckout.Command]): Behavior[OrderManager.Command] = Behaviors.receive((context, msg) =>
    msg match {
    case SelectDeliveryAndPaymentMethod(delivery, payment, sender) =>
      checkoutActorRef ! TypedCheckout.SelectDeliveryMethod(delivery)
      checkoutActorRef ! TypedCheckout.SelectPayment(payment, context.self)
      inPayment(sender)
    case _ =>
      Behaviors.same
  }
  )

  def inPayment(senderRef: ActorRef[Ack]): Behavior[OrderManager.Command] = Behaviors.receiveMessage {
    case ConfirmPaymentStarted(paymentRef) =>
      senderRef ! Done
      inPayment(paymentRef, senderRef)
    case ConfirmPaymentReceived =>
      senderRef ! Done
      finished
    case _ =>
      Behaviors.same
  }

  def inPayment(
    paymentActorRef: ActorRef[Payment.Command],
    senderRef: ActorRef[Ack]
  ): Behavior[OrderManager.Command] = Behaviors.setup { _ =>
    senderRef ! Done
    Behaviors.receiveMessage{
      case Pay(sender) =>
        paymentActorRef ! Payment.DoPayment
        inPayment(sender)
      case _ =>
        Behaviors.same
    }
  }

  def finished: Behavior[OrderManager.Command] = Behaviors.stopped
}
