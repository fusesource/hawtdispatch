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

/**
 * Provides Scala applications enhanced syntactic sugar to the HawtDispatch API.
 */
object ScalaDispatch {

  /**
   * Enriches the DispatchQueue interfaces with additional Scala friendly methods.
   */
  final class RichDispatchQueue(val queue: DispatchQueue) extends Proxy with Function1[Runnable, Unit]  {
    // Proxy
    def self: Any = queue

    // Function1[Runnable, Unit]
    def apply(task: Runnable) = queue.dispatchAsync(task)
  
    def <<(task: Runnable) = {apply(task); this}
    def ->:(task: Runnable) = {apply(task); this}
  }

  implicit def DispatchQueueWrapper(x: DispatchQueue) = new RichDispatchQueue(x)

  def getRandomThreadQueue = Dispatch.getRandomThreadQueue
  def getCurrentThreadQueue = Dispatch.getCurrentQueue
  def createSource[Event, MergedEvent](aggregator: EventAggregator[Event, MergedEvent], queue: DispatchQueue) =
    Dispatch.createSource(aggregator, queue)
  def createSource(channel: SelectableChannel, interestOps: Int, queue: DispatchQueue) =
    Dispatch.createSource(channel, interestOps, queue)
  def getCurrentQueue = Dispatch.getCurrentQueue
  def createQueue(label: String=null) = Dispatch.createQueue(label)
  def getGlobalQueue(priority: DispatchPriority) = Dispatch.getGlobalQueue(priority)
  def getGlobalQueue = Dispatch.getGlobalQueue

//  def dispatchMain = Dispatch.dispatchMain
//  def getMainQueue = Dispatch.getMainQueue

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

  def ^(proc: => Unit): Runnable = new Runnable() {
    def run() {
      proc;
    }
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

}
