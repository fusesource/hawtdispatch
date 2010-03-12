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

import java.util.concurrent.atomic.AtomicInteger
import java.nio.channels.SelectableChannel

object ScalaSupport {

  implicit def DispatchQueueWrapper(x: DispatchQueue) = new RichDispatchQueue(x)

  type Retained = org.fusesource.hawtdispatch.Retained
  type DispatchQueue = org.fusesource.hawtdispatch.DispatchQueue
  type DispatchSource = org.fusesource.hawtdispatch.DispatchSource
  type DispatchPriority = org.fusesource.hawtdispatch.DispatchPriority

  def mainQueue() = DispatchSystem.getMainQueue
  def globalQueue(priority: DispatchPriority=DispatchPriority.DEFAULT) = DispatchSystem.getGlobalQueue(priority)
  def createSerialQueue(name: String=null) = DispatchSystem.createSerialQueue(name)
  def createSource(channel:SelectableChannel, interestOps:Int, queue:DispatchQueue) = DispatchSystem.createSource(channel, interestOps, queue)

  def getRandomThreadQueue() = DispatchSystem.getRandomThreadQueue
  def getCurrentThreadQueue() = DispatchSystem.getCurrentThreadQueue
  def getCurrentQueue() = DispatchSystem.getCurrentQueue


  trait Service {
    def startup() = {}
    def shutdown() = {}
  }

  trait ServiceRetainer extends Retained {

    protected def retainedService: Service = new Service {}
    protected var releaseWatchers = List[Runnable]()

    val retained = new AtomicInteger(1);
    retainedService.startup

    override def retain = {
      assertRetained()
      retained.getAndIncrement()
    }

    override def release() = {
      assertRetained()
      if (retained.decrementAndGet() == 0) {
        retainedService.shutdown
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
      releaseWatchers = onRelease :: releaseWatchers;
    }

  }
  trait Queued {

    protected def using(resource: Retained): (=> Unit) => Runnable = {
      using(resource, resource) _
    }

    protected def using(resources: Seq[Retained]): (=> Unit) => Runnable = {
      using(resources, resources) _
    }

    protected def retaining(resource: Retained): (=> Unit) => Runnable = {
      using(resource, null) _
    }

    protected def retaining(resources: Seq[Retained]): (=> Unit) => Runnable = {
      using(resources, null) _
    }

    protected def releasing(resource: Retained): (=> Unit) => Runnable = {
      using(null, resource) _
    }

    protected def releasing(resources: Seq[Retained]): (=> Unit) => Runnable = {
      using(null, resources) _
    }

    protected def retain(retainedResources: Seq[Retained]) = {
      if (retainedResources != null) {
        for (resource <- retainedResources) {
          resource.retain
        }
      }
    }

    protected def release(releasedResources: Seq[Retained]) = {
      if (releasedResources != null) {
        for (resource <- releasedResources) {
          resource.release
        }
      }
    }

    protected def ^(proc: => Unit): Runnable = new Runnable() {
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

  }

  trait QueuedService extends Queued with Service {
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

  trait QueuedRetained extends Queued with ServiceRetainer {
    val queue: DispatchQueue

    protected override def retainedService = new Service {
      override def startup() = {
        queue.retain
      }

      override def shutdown() = {
        queue << ^ {onShutdown}
        queue.release
      }
    }

    protected def onShutdown() = {}
  }
}