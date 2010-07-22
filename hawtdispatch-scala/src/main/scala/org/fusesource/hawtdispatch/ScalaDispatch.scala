/**
 * Copyright (C) 2010, Progress Software Corporation and/or its
 * subsidiaries or affiliates.  All rights reserved.
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
package org.fusesource.hawtdispatch

import java.nio.channels.SelectableChannel
import java.util.concurrent.{Executor, TimeUnit}
import org.fusesource.hawtdispatch.ScalaDispatchHelpers.Callback

/**
 * Provides Scala applications enhanced syntactic sugar to the HawtDispatch API.
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
object ScalaDispatch {

  /**
   * Enriches the Executor interfaces with additional Scala friendly methods.
   */
  final class RichExecutor(val queue: Executor) extends Proxy {
    def self: Any = queue
    private def execute(task:Runnable):RichExecutor = {queue.execute(task); this}

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
    def apply(task: =>Unit):RichExecutor = execute(runnable(task _))

    /**
     * Same as {@link #apply(=>Unit)}
     */
    def ^(task: =>Unit):RichExecutor = execute(runnable(task _))

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
  }

  implicit def ExecutorWrapper(x: Executor) = new RichExecutor(x)

  /**
   *  Enriches the DispatchQueue interfaces with additional Scala friendly methods.
   */
  final class RichDispatchQueue(val queue: DispatchQueue) extends Proxy {
    // Proxy
    def self: Any = queue

    private def execute(task:Runnable):RichDispatchQueue = {queue.execute(task); this}

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
    def apply(task: =>Unit):RichDispatchQueue = execute(runnable(task _))

    /**
     * Same as {@link #apply(=>Unit)}
     */
    def ^(task: =>Unit):RichDispatchQueue = execute(runnable(task _))

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


    /**
     * Wraps the supplied function an callback object which is also a a partial function that when
     * called will cause the wrapped function to be asynchronously executed on the
     * dispatch queue. 
     */
    def wrap[T](func: (T)=>Unit) = Callback(queue, func)


  }
  
  implicit def DispatchQueueWrapper(x: DispatchQueue) = new RichDispatchQueue(x)

  /////////////////////////////////////////////////////////////////////
  //
  // re-export all the Dispatch static methods.
  //
  /////////////////////////////////////////////////////////////////////

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

