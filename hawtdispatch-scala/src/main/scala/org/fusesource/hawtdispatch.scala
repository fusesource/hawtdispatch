/**
 * Copyright (C) 2010, FuseSource Corp.  All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

/**
 * <p>
 * </p>
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
package object hawtdispatch {

  implicit def ExecutorWrapper(x: Executor) = new RichExecutor(x)
  implicit def DispatchQueueWrapper(x: DispatchQueue) = new RichDispatchQueue(x)

  trait RichExecutorTrait {

    protected def execute(task:Runnable):Unit

    /**
     * <p>
     * Submits a partial function for asynchronous execution on a dispatch queue.
     * </p><p>
     * Calls to {@link #dispatchAsync(Runnable)} always return immediately after the runnable has
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
    def apply(task: =>Unit) = execute(runnable(task _))

    /**
     * Same as {@link #apply(=>Unit)}
     */
    def ^(task: =>Unit) = execute(runnable(task _))

    /**
     * <p>
     * Submits a runnable for asynchronous execution on a dispatch queue.
     * </p>
     *
     * @param task
     * The runnable to submit to the dispatch queue.
     */
    def <<(task: Runnable) = execute(task)

    /**
     * A right-associative version of the {@link #<<(Runnable)} method
     */
    def >>:(task: Runnable) = execute(task)

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
    def future[T](func: =>T) = {
      val rc = Future[T]()
      apply {
        rc(func)
      }
      rc
    }

    def flatFuture[T](func: =>Future[T]) = {
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
        original.retain
        apply {
          try {
            val result = func
            original.apply {
              k(result)
            }
          } finally {
            original.release
          }
        }
      }
    }
  }


  /**
   * Enriches the Executor interfaces with additional Scala friendly methods.
   */
  final class RichExecutor(val executor: Executor) extends Proxy with RichExecutorTrait {
    def self: Any = executor
    protected def execute(task:Runnable) = executor.execute(task)
  }

  /**
   *  Enriches the DispatchQueue interfaces with additional Scala friendly methods.
   */
  final class RichDispatchQueue(val queue: DispatchQueue) extends Proxy with RichExecutorTrait {
    def self: Any = queue
    protected def execute(task:Runnable) = queue.execute(task)

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
    def after(time:Long, unit:TimeUnit)(task: =>Unit) = queue.dispatchAfter(time, unit, runnable(task _))

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
    def <<|(task: Runnable) = {
      if( queue.isExecuting ) {
        try {
          task.run
        } catch {
          case e:Exception =>
            e.printStackTrace
        }
      } else {
        queue.dispatchAsync(task);
      }
      this
    }

    /**
     * A right-associative version of the {@link #<<|(Runnable)} method
     */
    def |>>:(task: Runnable) = this <<| task
  }

  /////////////////////////////////////////////////////////////////////
  //
  // re-export all the Dispatch static methods.
  //
  /////////////////////////////////////////////////////////////////////

  /**
   * Same as {@link Dispatch.getThreadQueue}
   */
  def getThreadQueue(n:Int, priority:DispatchPriority=DispatchPriority.DEFAULT) = Dispatch.getThreadQueue(n, priority)

  /**
   * Same as {@link Dispatch.getRandomThreadQueue}
   */
  def getRandomThreadQueue = Dispatch.getRandomThreadQueue

  /**
   * Same as {@link Dispatch.getCurrentQueue}
   */
  def getCurrentThreadQueue = Dispatch.getCurrentQueue

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
  // Make it easier to create Runnable objects.
  //
  /////////////////////////////////////////////////////////////////////

  /**
   * Creates a runnable object from a partial function
   */
  def ^(proc: => Unit): Runnable = runnable(proc _)

  /**
   * Creates a runnable object from a partial function
   */
  implicit def runnable(proc: ()=>Unit): Runnable = new Runnable() {
    def run() {
      proc()
    }
  }

}
