/**
 * Copyright (C) 2009, Progress Software Corporation and/or its
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

import _root_.java.lang.String
import java.nio.channels.SelectableChannel
import java.util.concurrent.{CountDownLatch, Executor, TimeUnit}
import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.HashSet
import collection.mutable.ListBuffer

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
    def apply(task:Runnable):RichExecutor = {queue.execute(task); this}
    def apply(task: =>Unit):RichExecutor = apply(runnable(task _))
    def <<(task: Runnable) = apply(task)
    def >>:(task: Runnable) = apply(task)
  }

  implicit def ExecutorWrapper(x: Executor) = new RichExecutor(x)

  /**
   * Enriches the DispatchQueue interfaces with additional Scala friendly methods.
   */
  final class RichDispatchQueue(val queue: DispatchQueue) extends Proxy {
    // Proxy
    def self: Any = queue

    def apply(task:Runnable):RichDispatchQueue = {queue.execute(task); this}
    def apply(task: =>Unit):RichDispatchQueue = apply(runnable(task _))
    def ^(task: =>Unit):RichDispatchQueue = apply(runnable(task _))

    def <<(task: Runnable) = apply(task)
    def >>:(task: Runnable) = apply(task)

    def wrap[T](func: (T)=>Unit) = Callback(queue, func)
    def after(time:Long, unit:TimeUnit)(task: =>Unit) = queue.dispatchAfter(time, unit, runnable(task _))


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
    def |>>:(task: Runnable) = this << task
  }
  implicit def DispatchQueueWrapper(x: DispatchQueue) = new RichDispatchQueue(x)

  /////////////////////////////////////////////////////////////////////
  //
  // re-export all the Dispatch static methods.
  //
  /////////////////////////////////////////////////////////////////////

  def getRandomThreadQueue = Dispatch.getRandomThreadQueue
  def getCurrentThreadQueue = Dispatch.getCurrentQueue
  def createSource[Event, MergedEvent](aggregator: EventAggregator[Event, MergedEvent], queue: DispatchQueue) = {
    Dispatch.createSource(aggregator, queue)
  }
  def createSource(channel: SelectableChannel, interestOps: Int, queue: DispatchQueue) = {
    Dispatch.createSource(channel, interestOps, queue)
  }
  def getCurrentQueue = Dispatch.getCurrentQueue
  def createQueue(label: String=null) = Dispatch.createQueue(label)
  def getGlobalQueue(priority: DispatchPriority) = Dispatch.getGlobalQueue(priority)
  def getGlobalQueue = Dispatch.getGlobalQueue
  // def dispatchMain = Dispatch.dispatchMain
  // def getMainQueue = Dispatch.getMainQueue

  /////////////////////////////////////////////////////////////////////
  //
  // Make it easier to create Runnable objects.
  //
  /////////////////////////////////////////////////////////////////////

  def ^(proc: => Unit): Runnable = runnable(proc _)

  implicit def runnable(proc: ()=>Unit): Runnable = new Runnable() {
    def run() {
      proc()
    }
  }

  /////////////////////////////////////////////////////////////////////
  //
  // Helpers for working with Retained objects.
  //
  /////////////////////////////////////////////////////////////////////

  def using(resource: Retained): (=> Unit) => Runnable = {
    using(resource, resource) _
  }

  def using(resources: Seq[Retained]): (=> Unit) => Runnable = {
    using(resources, resources) _
  }

  def retaining(resource: Retained): (=> Unit) => Runnable = {
    using(resource, null) _
  }

  def retaining(resources: Seq[Retained]): (=> Unit) => Runnable = {
    using(resources, null) _
  }

  def releasing(resource: Retained): (=> Unit) => Runnable = {
    using(null, resource) _
  }

  def releasing(resources: Seq[Retained]): (=> Unit) => Runnable = {
    using(null, resources) _
  }

  private def using(retainedResource: Retained, releasedResource: Retained)(proc: => Unit): Runnable = {
    if (retainedResource != null) {
      retainedResource.retain
    }
    new Runnable() {
      def run = {
        try {
          proc;
        } finally {
          if (releasedResource != null) {
            releasedResource.release
          }
        }
      }
    }
  }

  private def using(retainedResources: Seq[Retained], releasedResources: Seq[Retained])(proc: => Unit): Runnable = {
    retain(retainedResources)
    new Runnable() {
      def run = {
        try {
          proc;
        } finally {
          release(releasedResources)
        }
      }
    }
  }

  def retain(retainedResources: Seq[Retained]) = {
    if (retainedResources != null) {
      for (resource <- retainedResources) {
        resource.retain
      }
    }
  }

  def release(releasedResources: Seq[Retained]) = {
    if (releasedResources != null) {
      for (resource <- releasedResources) {
        resource.release
      }
    }
  }


  class ListEventAggregator[T] extends EventAggregator[T, ListBuffer[T]] {
    def mergeEvent(previous:ListBuffer[T], event:T) = {
      if( previous == null ) {
        ListBuffer(event)
      } else {
        previous += event
      }
    }
    def mergeEvents(previous:ListBuffer[T], events:ListBuffer[T]):ListBuffer[T] = {
      previous ++= events
    }
  }

  /////////////////////////////////////////////////////////////////////
  //
  // Helpers for working with Callbacks.
  //
  /////////////////////////////////////////////////////////////////////

  sealed case class Callback[-A](retained: Retained, func: (A)=>Unit) extends (A => Unit) {
    override def apply(v1: A) = func(v1)
  }

  abstract sealed class Result[+T]
  case class Success[+T](value:T) extends Result[T]
  case class Failure(exception:Exception) extends Result[Nothing]

  def result[T](cb: (Result[T])=>Unit)(proc: => T): Runnable = {
    var resource: Retained = null
    if (cb != null ) {
      cb match {
        case Callback(retained, _)=>
          resource = retained
        case _=>
      }
    }
    if( resource != null ) {
      resource.retain
    }
    new Runnable() {
      def run = {
        try {
          val rc = proc;
          if( cb!=null ) {
            cb(Success(rc))
          }
        } catch {
          case e:Exception=>
            if( cb!=null ) {
              cb(Failure(e))
            }
        } finally {
          if (resource != null) {
            resource.release
          }
        }
      }
    }
  }

  def using[T](cb: (T)=>Unit)(proc: =>Unit): Runnable = {
    using(toRetained(cb))(proc _)
  }

  private def toRetained[T](cb: (T)=>Unit) = {
    if (cb != null ) {
      cb match {
        case Callback(retained, _)=>
          retained
        case _=>
          null
      }
    } else {
      null
    }
  }

  def reply[T](cb: (T)=>Unit)(proc: => T): Runnable = {
    var resource = toRetained(cb)
    if( resource != null ) {
      resource.retain
    }
    new Runnable() {
      def run = {
        try {
          val rc = proc;
          if( cb!=null ) {
            cb(rc)
          }
        } finally {
          if (resource != null) {
            resource.release
          }
        }
      }
    }
  }

}

import ScalaDispatch._

/**
 * Allows you to capture future results of an async computation.
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
class Future[T] extends (T => Unit) with ( ()=>T ) {

  @volatile
  var result:Option[T] = None
  var latch = new CountDownLatch(1)

  def apply(value:T) = {
    result = Some(value)
    latch.countDown
  }

  def apply() = {
    latch.await
    result.get
  }

  def apply(time:Long, unit:TimeUnit) = {
    if( latch.await(time, unit) ) {
      Some(result.get)
    } else {
      None
    }
  }

  def completed = result!=None
}
/**
 *
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
object Future {
  def apply[T](func: (T =>Unit)=>Unit) = {
    var future = new Future[T]()
    func(future)
    future()
  }
}

/**
 * <p>
 * A TaskTracker is used to track multiple async processing tasks and
 * call a callback once they all complete.
 * </p>
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
class TaskTracker(val name:String, val parent:DispatchQueue=getGlobalQueue) {

  var timeout: Long = 0
  private[this] val tasks = new HashSet[Runnable]()
  private[this] var _callback:Runnable = null
  val queue = parent.createSerialQueue("tracker: "+name);

  def task(name:String="unknown"):Runnable = {
    val rc = new Runnable() {
      def run = {
        remove(this)
      }
      override def toString = name
    }
    ^ {
      assert(_callback==null)
      tasks.add(rc)
    }  >>: queue
    return rc
  }

  def callback(handler: Runnable) {
    var start = System.currentTimeMillis
    ^ {
      _callback = handler
      checkDone()
    }  >>: queue

    def schedualCheck(timeout:Long):Unit = {
      if( timeout>0 ) {
        queue.after(timeout, TimeUnit.MILLISECONDS) {
          if( _callback!=null ) {
            schedualCheck(onTimeout(System.currentTimeMillis-start, tasks.toArray.toList.map(_.toString)))
          }
        }
      }
    }
    schedualCheck(timeout)
  }

  def callback(handler: =>Unit ) {
    callback(runnable(handler _))
  }

  /**
   * Subclasses can override if they want to log the timeout event.
   * the method should return the next timeout value.  If 0, then
   * it will not check for further timeouts.
   */
  protected def onTimeout(duration:Long, tasks: List[String]):Long = 0

  private def remove(r:Runnable) = ^{
    if( tasks.remove(r) ) {
      checkDone()
    }
  } >>: queue

  private def checkDone() = {
    if( tasks.isEmpty && _callback!=null ) {
      _callback >>: queue
      _callback = null
      queue.release
    }
  }

  def await() = {
    val latch =new CountDownLatch(1)
    callback {
      latch.countDown
    }
    latch.await
  }

  def await(timeout:Long, unit:TimeUnit) = {
    val latch = new CountDownLatch(1)
    callback {
      latch.countDown
    }
    latch.await(timeout, unit)
  }

  override def toString = tasks.synchronized { name+" waiting on: "+tasks }
}
