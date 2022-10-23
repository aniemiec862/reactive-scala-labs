package EShop.lab3

import EShop.lab2.{Cart, TypedCartActor, TypedCheckout}
import akka.actor.testkit.typed.Effect.{Spawned, TimerCancelled, TimerScheduled}
import akka.actor.testkit.typed.scaladsl.{BehaviorTestKit, ScalaTestWithActorTestKit, TestInbox}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class TypedCartTest
  extends ScalaTestWithActorTestKit
  with AnyFlatSpecLike
  with BeforeAndAfterAll
  with Matchers
  with ScalaFutures {

  override def afterAll: Unit =
    testKit.shutdownTestKit()

  import TypedCartActor._

  //use GetItems command which was added to make test easier
  it should "add item properly" in {
    val testKit = BehaviorTestKit(TypedCartActor())
    val inbox = TestInbox[Cart]()

    testKit.run(TypedCartActor.AddItem("item1"))
    testKit.run(TypedCartActor.AddItem("item2"))
    testKit.run(TypedCartActor.GetItems(inbox.ref))
    inbox.expectMessage(Cart(Seq("item1", "item2")))
  }

  it should "be empty after adding and removing the same item" in {
    val testKit = BehaviorTestKit(TypedCartActor())
    val inbox = TestInbox[Cart]()

    testKit.run(TypedCartActor.AddItem("item1"))
    testKit.run(TypedCartActor.RemoveItem("item1"))
    testKit.run(TypedCartActor.GetItems(inbox.ref))
    inbox.expectMessage(Cart.empty)
  }

  it should "start checkout" in {

  }
}
