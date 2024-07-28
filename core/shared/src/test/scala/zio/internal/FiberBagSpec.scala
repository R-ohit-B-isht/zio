package zio.internal

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import zio.{Fiber, FiberId, UIO, Chunk, Exit, Trace, ZIO}

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

    fibers.par.foreach(bag.add)
    bag.iterate().size should be(1000)

    fibers.par.foreach(bag.remove)
    bag.iterate().size should be(0)
  }

  it should "support concurrent iteration over the set of fibers" in {
    val bag = new FiberBag()
    val fibers = (1 to 1000).map(i => new MockFiber(FiberId.None))

    fibers.foreach(bag.add)
    val iteratedFibers = bag.iterate().par.toList

    iteratedFibers.size should be(1000)
    iteratedFibers should contain allElementsOf fibers
  }
}
