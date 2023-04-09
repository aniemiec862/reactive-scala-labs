package EShop.lab5

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}

import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success}

object PaymentService {

  sealed trait Response
  case object PaymentSucceeded extends Response

  case class PaymentClientError() extends Exception
  case class PaymentServerError() extends Exception

  // actor behavior which needs to be supervised
  // use akka.http.scaladsl.Http to make http based payment request
  // use getUri method to obtain url
  def apply(
    method: String,
    payment: ActorRef[Response]
  ): Behavior[HttpResponse] = Behaviors.setup { context =>
    val http = Http(context.system)
    val result = http.singleRequest(HttpRequest(uri = getURI(method)))
    context.pipeToSelf(result) {
      case Success(value) => value
      case Failure(e) => throw e
    }
    Behaviors.receiveMessage {
      case HttpResponse(code, _, _, _) =>
        code.intValue() match {
          case 200 =>
            payment ! PaymentSucceeded
            Behaviors.stopped
          case codeValue if codeValue == 408 || codeValue >= 500 => throw PaymentServerError()
          case codeValue if codeValue >= 400 && codeValue < 500 => throw PaymentClientError()
        }
    }
  }

  // remember running PymentServiceServer() before trying payu based payments
  private def getURI(method: String) = method match {
    case "payu"   => "http://127.0.0.1:8080"
    case "paypal" => s"http://httpbin.org/status/408"
    case "visa"   => s"http://httpbin.org/status/200"
    case _        => s"http://httpbin.org/status/404"
  }
}
