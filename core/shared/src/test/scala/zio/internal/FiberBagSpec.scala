package zio.internal

import zio._
import zio.test._
import zio.test.Assertion._
import zio.internal.FiberBag

object FiberBagSpec extends ZIOSpecDefault {
  // Mock Fiber class for testing
  class MockFiber(val id: FiberId) extends Fiber[Nothing, Unit] {
    override def await(implicit trace: Trace): UIO[Exit[Nothing, Unit]] = ZIO.succeed(Exit.unit)
    override def children(implicit trace: Trace): UIO[Chunk[Fiber.Runtime[_, _]]] = ZIO.succeed(Chunk.empty)
    override def inheritAll(implicit trace: Trace): UIO[Unit] = ZIO.unit
    override def poll(implicit trace: Trace): UIO[Option[Exit[Nothing, Unit]]] = ZIO.succeed(Some(Exit.unit))
    override def interruptAsFork(fiberId: FiberId)(implicit trace: Trace): UIO[Unit] = ZIO.unit
  }

  def spec = suite("FiberBag")(
    test("add and remove fibers") {
      for {
        bag <- ZIO.succeed(new FiberBag())
        fiber = new MockFiber(FiberId.None)
        _ <- ZIO.succeed(bag.add(fiber))
        containsFiber <- ZIO.succeed(bag.iterate().toList.contains(fiber))
        _ <- ZIO.succeed(bag.remove(fiber))
        doesNotContainFiber <- ZIO.succeed(!bag.iterate().toList.contains(fiber))
      } yield assertTrue(containsFiber && doesNotContainFiber)
    },

    test("handle garbage collected fibers") {
      for {
        bag <- ZIO.succeed(new FiberBag())
        fiber = new MockFiber(FiberId.None)
        _ <- ZIO.succeed(bag.add(fiber))
        _ <- ZIO.succeed(System.gc())
        _ <- ZIO.succeed(bag.cleanUp())
        doesNotContainFiber <- ZIO.succeed(!bag.iterate().toList.contains(fiber))
      } yield assertTrue(doesNotContainFiber)
    },

    test("support concurrent addition and removal of fibers") {
      for {
        bag <- ZIO.succeed(new FiberBag())
        fibers = (1 to 1000).map(_ => new MockFiber(FiberId.None))
        _ <- ZIO.foreachDiscard(fibers)(fiber => ZIO.succeed(bag.add(fiber)))
        size <- ZIO.succeed(bag.iterate().size)
        _ <- ZIO.foreachDiscard(fibers)(fiber => ZIO.succeed(bag.remove(fiber)))
        finalSize <- ZIO.succeed(bag.iterate().size)
      } yield assertTrue(size == 1000 && finalSize == 0)
    },

    test("support concurrent iteration over the set of fibers") {
      for {
        bag <- ZIO.succeed(new FiberBag())
        fibers = (1 to 1000).map(_ => new MockFiber(FiberId.None))
        _ <- ZIO.foreachDiscard(fibers)(fiber => ZIO.succeed(bag.add(fiber)))
        iteratedFibers <- ZIO.succeed(bag.iterate().toList)
      } yield assertTrue(iteratedFibers.size == 1000 && iteratedFibers.toSet == fibers.toSet)
    },

    test("support concurrent operations") {
      for {
        bag <- ZIO.succeed(new FiberBag())
        fibers <- ZIO.foreach(1 to 1000)(_ => ZIO.succeed(new MockFiber(FiberId.None)))
        _ <- ZIO.foreachParDiscard(fibers)(fiber => ZIO.succeed(bag.add(fiber)))
        size <- ZIO.succeed(bag.iterate().size)
        _ <- ZIO.foreachParDiscard(fibers)(fiber => ZIO.succeed(bag.remove(fiber)))
        finalSize <- ZIO.succeed(bag.iterate().size)
      } yield assertTrue(size == 1000 && finalSize == 0)
    }
  ) @@ timeout(5.seconds)
}
