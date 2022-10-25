package EShop.lab3

import EShop.lab2
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
  it should "add item properly in sync" in {
    val cartActorTestKit = BehaviorTestKit(TypedCartActor())
    val inbox = TestInbox[Cart]()

    cartActorTestKit.run(AddItem("item1"))
    cartActorTestKit.run(AddItem("item2"))
    cartActorTestKit.run(GetItems(inbox.ref))

    inbox.expectMessage(Cart(Seq("item1", "item2")))
  }

  it should "add item properly in async" in {
    val cartActor = testKit.spawn(TypedCartActor(), "cartActor")
    val cartActorProbe = testKit.createTestProbe[Cart]()

    cartActor ! AddItem("item1")
    cartActor ! AddItem("item2")
    cartActor ! GetItems(cartActorProbe.ref)

    cartActorProbe.expectMessage(Cart(Seq("item1", "item2")))
  }

  it should "be empty after adding and removing the same item in sync" in {
    val cartActorTestKit = BehaviorTestKit(TypedCartActor())
    val inbox = TestInbox[Cart]()

    cartActorTestKit.run(AddItem("item1"))
    cartActorTestKit.run(RemoveItem("item1"))
    cartActorTestKit.run(GetItems(inbox.ref))

    inbox.expectMessage(Cart.empty)
  }

  it should "be empty after adding and removing the same item in async" in {
    val cartActor = testKit.spawn(TypedCartActor(), "cartActor")
    val cartActorProbe = testKit.createTestProbe[Cart]()

    cartActor ! AddItem("item1")
    cartActor ! RemoveItem("item1")
    cartActor ! GetItems(cartActorProbe.ref)

    cartActorProbe.expectMessage(Cart.empty)
  }

  it should "start checkout in sync" in {
    val cartActorTestKit = BehaviorTestKit(TypedCartActor())
    val inbox = TestInbox[TypedCartActor.Command]()

    cartActorTestKit.run(AddItem("item1"))
    cartActorTestKit.run(StartCheckout(testKit.createTestProbe[TypedCartActor.Event]().ref))

    assert(inbox.hasMessages)
    val message = inbox.receiveMessage()
    assert(message.isInstanceOf[TypedCartActor.CheckoutStarted])
  }

  it should "start checkout in async" in {
    val cartActor = testKit.spawn(TypedCartActor(), "cartActor")
    val cartActorProbe = testKit.createTestProbe[TypedCartActor.Command]()
    val checkoutProbe = testKit.createTestProbe[TypedCheckout.Command]()

    cartActor ! AddItem("item1")
    cartActor ! StartCheckout(testKit.createTestProbe[TypedCartActor.Event]().ref)

//    cartActorProbe.expectMessage(TypedCartActor.CheckoutStarted(checkoutProbe.ref))
  }
}
