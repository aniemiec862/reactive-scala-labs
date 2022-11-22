package EShop.lab5

import EShop.lab2.TypedCheckout
import EShop.lab3.OrderManager
import EShop.lab5.PaymentService.PaymentSucceeded
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed._

import scala.concurrent.duration._

object Payment {
  sealed trait Message
  case object DoPayment                                                       extends Message
  case class WrappedPaymentServiceResponse(response: PaymentService.Response) extends Message

  sealed trait Response
  case object PaymentRejected extends Response

  val restartStrategy: RestartSupervisorStrategy = SupervisorStrategy.restart.withLimit(maxNrOfRetries = 3, withinTimeRange = 1.second)

  val stopStrategy: SupervisorStrategy = SupervisorStrategy.stop

  def apply(
    method: String,
    orderManager: ActorRef[OrderManager.Command],
    checkout: ActorRef[TypedCheckout.Command]
  ): Behavior[Message] =
    Behaviors
      .receive[Message](
        (context, msg) =>
          msg match {
            case DoPayment =>
              val adapter: ActorRef[PaymentService.Response] = context.messageAdapter {
                case PaymentSucceeded =>
                  WrappedPaymentServiceResponse(PaymentSucceeded)
              }

              val paymentService = Behaviors
                .supervise(Behaviors
                  .supervise(PaymentService(method, adapter))
                  .onFailure[PaymentService.PaymentServerError](stopStrategy))
                .onFailure[PaymentService.PaymentClientError](restartStrategy)
              val paymentServiceRef = context.spawnAnonymous(paymentService)
              context.watch(paymentServiceRef)
              Behaviors.same

            case WrappedPaymentServiceResponse(PaymentSucceeded) =>
              orderManager ! OrderManager.ConfirmPaymentReceived
              Behaviors.same
        }
      )
      .receiveSignal {
        case (_, Terminated(_)) =>
          notifyAboutRejection(orderManager, checkout)
          Behaviors.same
      }

  // please use this one to notify when supervised actor was stoped
  private def notifyAboutRejection(
    orderManager: ActorRef[OrderManager.Command],
    checkout: ActorRef[TypedCheckout.Command]
  ): Unit = {
    orderManager ! OrderManager.PaymentRejected
    checkout ! TypedCheckout.PaymentRejected
  }

}
