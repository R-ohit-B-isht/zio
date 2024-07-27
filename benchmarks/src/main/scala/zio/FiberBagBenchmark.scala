package zio

import org.openjdk.jmh.annotations._
import zio.internal.FiberBag
import zio._
import zio.ZIO
import zio.Trace

@State(org.openjdk.jmh.annotations.Scope.Thread)
class FiberBagBenchmark {
  var bag: FiberBag = _

  // Mock Fiber class for benchmark tests
  class MockFiber(id: FiberId) extends Fiber[Nothing, Unit] {
    override def id: FiberId = id
    override def await(implicit trace: Trace): UIO[Exit[Nothing, Unit]] = ZIO.succeed(Exit.unit)
    override def children(implicit trace: Trace): UIO[Chunk[Fiber.Runtime[_, _]]] = ZIO.succeed(Chunk.empty)
    override def inheritAll(implicit trace: Trace): UIO[Unit] = ZIO.unit
    override def poll(implicit trace: Trace): UIO[Option[Exit[Nothing, Unit]]] = ZIO.succeed(Some(Exit.unit))
    override def interruptAsFork(fiberId: FiberId)(implicit trace: Trace): UIO[Unit] = ZIO.unit
  }

  @Setup(Level.Trial)
  def setup(): Unit = {
    bag = new FiberBag()
  }

  @Benchmark
  def addBenchmark(): Unit = {
    bag.add(new MockFiber(FiberId.None))
  }

  @Benchmark
  def removeBenchmark(): Unit = {
    val fiber = new MockFiber(FiberId.None)
    bag.add(fiber)
    bag.remove(fiber)
  }

  @Benchmark
  def iterateBenchmark(): Unit = {
    bag.iterate().foreach(_ => ())
  }
}
