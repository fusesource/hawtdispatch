/**
 * Copyright (C) 2012 FuseSource, Inc.
 * http://fusesource.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.fusesource

import org.fusesource.hawtdispatch._
import java.nio.channels.SelectableChannel
import scala.util.continuations._
import java.util.concurrent.{ExecutorService, CountDownLatch, Executor, TimeUnit}
import java.util.concurrent.atomic.AtomicBoolean
import java.io.Closeable

/**
 * <p>
 * </p>
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
package object hawtdispatch {

  implicit def ExecutorWrapper(x: Executor) = new RichExecutor(x)
  implicit def DispatchQueueWrapper(x: DispatchQueue) = new RichDispatchQueue(x)
  implicit def RichDispatchSourceWrapper(x: DispatchSource) = new RichDispatchSource(x)

  trait RichDispatchObject {
    def actual:DispatchObject

    def target_=(queue: DispatchQueue) { actual.setTargetQueue( queue ) }
    def target:DispatchQueue = actual.getTargetQueue
  }

  trait RichExecutorTrait {

    protected def execute(task:Task):Unit
    protected def execute(runnable:Runnable):Unit

    /**
     * <p>
     * Submits a partial function for asynchronous execution on a dispatch queue.
     * </p><p>
     * Calls to {@link #execute(Task)} always return immediately after the runnable has
     * been submitted, and never wait for the runnable to be executed.
     * </p><p>
     * The target queue determines whether the runnable will be invoked serially or
     * concurrently with respect to other runnables submitted to that same queue.
     * Serial queues are processed concurrently with with respect to each other.
     * </p><p>
     * The system will retain this queue until the runnable has finished.
     * </p>
     *
     * @param task
     * The function to submit to the dispatch queue.
     */
    def apply(task: =>Unit) = execute(r(task _))

    /**
     * Creates a Task object which executes the supplied partial
     * function on this executor when run.
     */
    def runnable(task: =>Unit) = new Task() {
      val target = r(task _)
      def run: Unit = {
        execute(target)
      }
    }

    /**
     * Same as {@link #apply(=>Unit)}
     */
    def ^(task: =>Unit) = execute(r(task _))

    /**
     * <p>
     * Submits a runnable for asynchronous execution on a dispatch queue.
     * </p>
     *
     * @param task
     * The runnable to submit to the dispatch queue.
     */
    def <<(task: Runnable) = execute(task)
    def <<(task: Task) = execute(task)

    /**
     * A right-associative version of the {@link #<<(Runnable)} method
     */
    def >>:(task: Runnable) = execute(task)
    def >>:(task: Task) = execute(task)

    /**
     * Executes the supplied function on the dispatch queue
     * while blocking the calling thread as it waits for the response.
     */
    def sync[T](func: =>T): T = future(func)()

    /**
     * Executes the supplied function on the dispatch queue
     * and returns a Future that can be used to wait on the future
     * result of the function.
     */
    def future[T](func: =>T):Future[T] = {
      val rc = Future[T]()
      apply {
        rc(func)
      }
      rc
    }

    def flatFuture[T](func: =>Future[T]):Future[T] = {
      val rc = Future[T]()
      apply {
        func.onComplete(rc(_))
      }
      rc
    }

    /**
     * Executes the supplied function on this executor.  If not called from a
     * runnable being exectued in a Dispatch Queue, then is call blocks
     * until continuation is executed.  Otherwise, the continuation is
     * resumed on the original calling dispatch queue once supplied function
     * completes.
     */
    def ![T](func: =>T): T @suspendable = shift { k: (T=>Unit) =>
      val original = getCurrentQueue
      if( original==null ) {
        k(sync(func))
      } else {
        apply {
          val result = func
          original.apply {
            k(result)
          }
        }
      }
    }

    /**
     * Same as {@link #future(=>T)} except that the partial function is wrapped in a {@link reset} block.
     */
    def !![T](func: =>T @suspendable):Future[T] = reset_future { func }

  }

  class RichDispatchSource(val actual:DispatchSource) extends Proxy with RichDispatchObject {
    def self = actual

    def onEvent(task: =>Unit) { actual.setEventHandler( r(task _) ) }
    def onCancel(task: =>Unit) { actual.setCancelHandler( r(task _) ) }

  }

  /**
   * Enriches the Executor interfaces with additional Scala friendly methods.
   */
  final class RichExecutor(val executor: Executor) extends Proxy with RichExecutorTrait {
    def self: Any = executor
    protected def execute(task:Task) = executor.execute(task)
    protected def execute(task:Runnable) = executor.execute(task)
  }

  /**
   *  Enriches the DispatchQueue interfaces with additional Scala friendly methods.
   */
  final class RichDispatchQueue(val actual: DispatchQueue) extends Proxy with RichExecutorTrait with RichDispatchObject {
    def self = actual
    protected def execute(task:Runnable) = actual.execute(task)
    protected def execute(task:Task) = actual.execute(task)

    def label_=(value: String) { actual.setLabel( value ) }
    def label:String = actual.getLabel

    /**
     * <p>
     * Submits a partial function for asynchronous execution on a dispatch queue after
     * the specified time delay.
     * </p>
     *
     * @param time
     * The amount of time to delay
     * @param unit
     * The units of time the delay is specified in
     * @param task
     * The runnable to submit to the dispatch queue.
     */
    def after(time:Long, unit:TimeUnit)(task: =>Unit) = actual.executeAfter(time, unit, r(task _))

    /**
     * <p>
     * Submits a partial function for repetitive asynchronous execution on a dispatch queue
     * each time specified time delay elapses.  Returns a Closable which when closed will
     * stop future executions of the task.
     * </p>
     *
     * @param time
     * The amount of time to delay
     * @param unit
     * The units of time the delay is specified in
     * @param task
     * The runnable to submit to the dispatch queue.
     */
    def repeatAfter(time:Long, unit:TimeUnit)(task: =>Unit):Closeable = new Closeable {
      val closed = new AtomicBoolean
      def close: Unit = closed.set(true)

      val action:Task = new Task() {
        def run: Unit = {
          if (!closed.get) {
            try {
              task
            } catch {
              case e:Throwable => e.printStackTrace
            }
            if (!closed.get) {
              actual.executeAfter(time, unit, action)
            }
          }
        }
      }

      actual.executeAfter(time, unit, action)
    }

    /**
     * <p>
     * Submits a runnable for asynchronous execution on a dispatch queue if the
     * queue is not currently executing, otherwise if the queue is currently executing,
     * then the runnable is directly executed.
     * </p>
     *
     * @param task
     * The runnable to submit to execute
     */
    def <<|(task: Task) = {
      if( actual.isExecuting ) {
        try {
          task.run
        } catch {
          case e:Exception =>
            e.printStackTrace
        }
      } else {
        actual.execute(task);
      }
      this
    }

    def <<|(task: Runnable):RichDispatchQueue = this <<|(new TaskWrapper(task))

    /**
     * <p>
     * Submits a runnable for asynchronous execution on a dispatch queue if the
     * queue is not currently executing, otherwise if the queue is currently executing,
     * then the runnable is directly executed.
     * </p>
     *
     * @param task
     * The runnable to submit to execute
     */
    def | (task: =>Unit ) = {
      this.<<|( r(task _))
    }

    /**
     * A right-associative version of the {@link #<<|(Runnable)} method
     */
    def |>>:(task: Runnable) =this <<| task
    def |>>:(task: Task) = this <<| task

  }

  /////////////////////////////////////////////////////////////////////
  //
  // re-export all the Dispatch static methods.
  //
  /////////////////////////////////////////////////////////////////////

  /**
   * Same as {@link Dispatch.getThreadQueues}
   */
  def getThreadQueues(priority:DispatchPriority=DispatchPriority.DEFAULT) = Dispatch.getThreadQueues(priority)

  /**
   * Same as {@link Dispatch.getCurrentQueue}
   */
  def getCurrentThreadQueue = Dispatch.getCurrentThreadQueue

  /**
   * Same as {@link Dispatch.createSource(EventAggregator, DispatchQueue)}
   */
  def createSource[Event, MergedEvent](aggregator: EventAggregator[Event, MergedEvent], queue: DispatchQueue) = {
    Dispatch.createSource(aggregator, queue)
  }

  /**
   * Same as {@link Dispatch.createSource(SelectableChannel, Int, DispatchQueue)}
   */
  def createSource(channel: SelectableChannel, interestOps: Int, queue: DispatchQueue) = {
    Dispatch.createSource(channel, interestOps, queue)
  }

  /**
   * Same as {@link Dispatch.getCurrentQueue}
   */
  def getCurrentQueue = Dispatch.getCurrentQueue

  /**
   * Same as {@link Dispatch.createQueue(String)}
   */
  def createQueue(label: String=null) = Dispatch.createQueue(label)

  /**
   * Same as {@link Dispatch.getGlobalQueue(DispatchPriority)}
   */
  def getGlobalQueue(priority: DispatchPriority=DispatchPriority.DEFAULT) = Dispatch.getGlobalQueue(priority)

  /**
   * Same as {@link Dispatch.getGlobalQueue }
   */
  def globalQueue = Dispatch.getGlobalQueue

  /**
   * Same as {@link Dispatch.NOOP }
   */
  def NOOP = Dispatch.NOOP


  /////////////////////////////////////////////////////////////////////
  //
  // Make it easier to create Task objects.
  //
  /////////////////////////////////////////////////////////////////////

  /**
   * Creates a runnable object from a partial function
   */
  def ^(proc: => Unit): Task = r(proc _)

  /**
   * Creates a runnable object from a partial function
   */
  private def r(proc: ()=>Unit): Task = new Task() {
    def run() {
      proc()
    }
  }

  /**
   * resets a CPS block, and returns it's result in a future.
   */
  def reset_future[T](func: =>T @suspendable):Future[T] = {
    val rc = Future[T]()
    reset {
      val r = func
      rc(r)
    }
    rc
  }
}
