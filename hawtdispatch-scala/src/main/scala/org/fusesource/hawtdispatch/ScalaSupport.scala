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

import internal.Dispatcher
import java.util.concurrent.atomic.AtomicInteger
import java.nio.channels.SelectableChannel

object ScalaSupport {

  val dispatcher:Dispatcher = Dispatch.DISPATCHER;

  implicit def DispatchQueueWrapper(x: DispatchQueue) = new RichDispatchQueue(x)

  type Retained = org.fusesource.hawtdispatch.Retained
  type DispatchQueue = org.fusesource.hawtdispatch.DispatchQueue
  type DispatchSource = org.fusesource.hawtdispatch.DispatchSource
  type DispatchPriority = org.fusesource.hawtdispatch.DispatchPriority
  type EventAggregator[Event, MergedEvent] = org.fusesource.hawtdispatch.EventAggregator[Event, MergedEvent]

  def mainQueue() = dispatcher.getMainQueue
  def globalQueue(priority: DispatchPriority=DispatchPriority.DEFAULT) = dispatcher.getGlobalQueue(priority)
  def createQueue(name: String=null) = dispatcher.createQueue(name)
  def createSource(channel:SelectableChannel, interestOps:Int, queue:DispatchQueue) = dispatcher.createSource(channel, interestOps, queue)
  def createSource[Event, MergedEvent](aggregator:EventAggregator[Event,MergedEvent], queue:DispatchQueue) = dispatcher.createSource(aggregator, queue)  

  def getRandomThreadQueue() = dispatcher.getRandomThreadQueue
  def getCurrentThreadQueue() = dispatcher.getCurrentThreadQueue
  def getCurrentQueue() = dispatcher.getCurrentQueue


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


  trait Service {
    def startup() = {}
    def shutdown() = {}
  }

  trait BaseRetained extends Retained {
    protected val retained = new AtomicInteger(1);
    protected var releaseWatchers = List[Runnable]()

    override def retain = {
      assertRetained()
      retained.getAndIncrement()
    }

    override def release() = {
      assertRetained()
      if (retained.decrementAndGet() == 0) {
        for( onRelease <- releaseWatchers) {
          onRelease.run
        }
      }
    }

    override def isReleased() = {
      retained.get() <= 0;
    }

    protected def assertRetained() {
      if (retained.get() <= 0) {
        throw new IllegalStateException(format("%s: Use of object not allowed after it has been released", this.toString()));
      }
    }

    override def addReleaseWatcher(onRelease: Runnable) {
      assertRetained()
      releaseWatchers = onRelease :: releaseWatchers;
    }

  }

  trait QueuedService extends Service {
    val queue: DispatchQueue

    override def startup() = {
      queue.retain
    }

    override def shutdown() = {
      queue << ^ {onShutdown}
      queue.release
    }

    protected def onShutdown() = {}
  }

  case class ListEventAggregator[T] extends EventAggregator[T, List[T]] {
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