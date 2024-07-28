package zio.internal

import java.util.concurrent.ConcurrentHashMap
import java.lang.ref.{WeakReference, ReferenceQueue}
import scala.jdk.CollectionConverters._
import zio.{Fiber, FiberId}
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class FiberBag {
  private val fibers = new ConcurrentHashMap[FiberId, WeakReference[Fiber[_, _]]]()
  private val referenceQueue = new ReferenceQueue[Fiber[_, _]]()
  private val scheduler = Executors.newScheduledThreadPool(1)

  // Schedule cleanUp to run at regular intervals
  scheduler.scheduleAtFixedRate(() => cleanUp(), 1, 1, TimeUnit.MINUTES)

  def add(fiber: Fiber[_, _]): Unit = {
    fibers.put(fiber.id, new WeakReference(fiber, referenceQueue))
  }

  def remove(fiber: Fiber[_, _]): Unit = {
    fibers.remove(fiber.id)
  }

  def cleanUp(): Unit = {
    var ref = referenceQueue.poll()
    while (ref != null) {
      val fiber = ref.asInstanceOf[WeakReference[Fiber[_, _]]].get()
      if (fiber != null) fibers.remove(fiber.id)
      ref = referenceQueue.poll()
    }
  }

  def iterate(): Iterable[Fiber[_, _]] = {
    fibers.values().asScala.flatMap(ref => Option(ref.get()))
  }
}
