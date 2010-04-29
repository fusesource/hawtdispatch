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
import java.util.concurrent.{Executor, TimeUnit}

/**
 * Provides Scala applications enhanced syntactic sugar to the HawtDispatch API.
 */
object ScalaDispatch {

  /**
   * Enriches the Executor interfaces with additional Scala friendly methods.
   */
  final class RichExecutor(val executor: Executor) extends Proxy {
    def self: Any = executor
    def apply(task: =>Unit) = executor.execute(^(task _))
  }
  implicit def ExecutorWrapper(x: Executor) = new RichExecutor(x)

  /**
   * Enriches the DispatchQueue interfaces with additional Scala friendly methods.
   */
  final class RichDispatchQueue(val queue: DispatchQueue) extends Proxy {
    // Proxy
    def self: Any = queue

    def apply(task: =>Unit) = queue.dispatchAsync(^(task _))
    def wrap[T](func: (T)=>Unit) = Callback(queue, func)
    def after(time:Long, unit:TimeUnit)(task: =>Unit) = queue.dispatchAfter(time, unit, ^(task _))

    def <<(task: Runnable) = {queue.dispatchAsync(task); this}
    def >>:(task: Runnable) = {queue.dispatchAsync(task); this}

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

    def |>>:(task: Runnable) = {
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

  def ^(proc: => Unit): Runnable = new Runnable() {
    def run() {
      proc;
    }
  }

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


  class ListEventAggregator[T] extends EventAggregator[T, List[T]] {
    def mergeEvent(previous:List[T], event:T) = {
      if( previous == null ) {
        event :: Nil
      } else {
        previous ::: List(event)
      }
    }
    def mergeEvents(previous:List[T], events:List[T]):List[T] = {
      previous ::: events
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

  def callback[T](cb: (T)=>Unit)(proc: => T): Runnable = {
    var resource: Retained = null
    if (cb != null ) {
      cb match {
        case Callback(retained, _)=>
          resource = retained
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
