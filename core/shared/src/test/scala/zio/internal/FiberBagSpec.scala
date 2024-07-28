package zio.internal

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import zio.{Fiber, FiberId, UIO, Chunk, Exit, Trace, ZIO, Runtime, Unsafe}
import zio.test.TestAspect._

class FiberBagSpec extends AnyFlatSpec with Matchers {
  // Mock Fiber class for testing
  class MockFiber(val id: FiberId) extends Fiber[Nothing, Unit] {
    override def await(implicit trace: Trace): UIO[Exit[Nothing, Unit]] = ZIO.succeed(Exit.unit)
    override def children(implicit trace: Trace): UIO[Chunk[Fiber.Runtime[_, _]]] = ZIO.succeed(Chunk.empty)
    override def inheritAll(implicit trace: Trace): UIO[Unit] = ZIO.unit
    override def poll(implicit trace: Trace): UIO[Option[Exit[Nothing, Unit]]] = ZIO.succeed(Some(Exit.unit))
    override def interruptAsFork(fiberId: FiberId)(implicit trace: Trace): UIO[Unit] = ZIO.unit
  }

  "FiberBag" should "add and remove fibers" in {
    val bag = new FiberBag()
    val fiber = new MockFiber(FiberId.None)

    bag.add(fiber)
    bag.iterate().toList should contain(fiber)

    bag.remove(fiber)
    bag.iterate().toList should not contain fiber
  }

  it should "handle garbage collected fibers" in {
    val bag = new FiberBag()
    var fiber = new MockFiber(FiberId.None)

    bag.add(fiber)
    fiber = null // simulate garbage collection
    System.gc()

    bag.cleanUp()
    bag.iterate().toList should not contain fiber
  }

  it should "support concurrent addition and removal of fibers" in {
    val bag = new FiberBag()
    val fibers = (1 to 1000).map(i => new MockFiber(FiberId.None))

    fibers.foreach(fiber => bag.add(fiber))
    bag.iterate().size should be(1000)

    fibers.foreach(fiber => bag.remove(fiber))
    bag.iterate().size should be(0)
  }

  it should "support concurrent iteration over the set of fibers" in {
    val bag = new FiberBag()
    val fibers = (1 to 1000).map(i => new MockFiber(FiberId.None))

    fibers.foreach(bag.add)
    val iteratedFibers = bag.iterate().toList

    iteratedFibers.size should be(1000)
    iteratedFibers should contain allElementsOf fibers
  }

  it should "support concurrent operations using ZIO" in {
    val runtime = Runtime.default
    Unsafe.unsafe { implicit unsafe =>
      runtime.unsafe.run(
        for {
          bag <- ZIO.succeed(new FiberBag())
          fibers <- ZIO.foreach(1 to 1000)(_ => ZIO.succeed(new MockFiber(FiberId.None)))
          _ <- ZIO.foreachParDiscard(fibers)(fiber => ZIO.succeed(bag.add(fiber)))
          size <- ZIO.succeed(bag.iterate().size)
          _ <- ZIO.foreachParDiscard(fibers)(fiber => ZIO.succeed(bag.remove(fiber)))
          finalSize <- ZIO.succeed(bag.iterate().size)
        } yield {
          size should be(1000)
          finalSize should be(0)
        }
      ).getOrThrowFiberFailure()
    }
  } @@ timeout(5.seconds)
}
