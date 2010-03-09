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
package org.fusesource.hawttasks.scala

import java.util.concurrent.atomic.AtomicInteger
import org.fusesource.hawttasks.{RefCounted, DispatchQueue, DispatchSystem}

trait Service {
  def startup() = {}

  def shutdown() = {}
}

trait ServiceRetainer extends RefCounted {

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

object TaskQueue {
  def apply(): TaskQueue = apply(null.asInstanceOf[String])

  def apply(name: String): TaskQueue =
    apply(DispatchSystem.createSerialQueue(name))

  def apply(queue: DispatchQueue): TaskQueue = new TaskQueue {
    def apply(task: Runnable) = queue.dispatchAsync(task)

    override def retain = queue.retain

    override def release = queue.release

    override def isReleased = queue.isReleased

    override def addReleaseWatcher(onRelease:Runnable) = queue.addReleaseWatcher(onRelease)
  }
}

trait TaskQueue extends Function1[Runnable, Unit] with RefCounted {
  def <<(task: Runnable) = {apply(task); this}

  def ->:(task: Runnable) = {apply(task); this}
}

trait Queued {
  var queue: TaskQueue

  protected def using(resource: RefCounted): (=> Unit) => Runnable = {
    using(resource, resource) _
  }

  protected def using(resources: Seq[RefCounted]): (=> Unit) => Runnable = {
    using(resources, resources) _
  }

  protected def retaining(resource: RefCounted): (=> Unit) => Runnable = {
    using(resource, null) _
  }

  protected def retaining(resources: Seq[RefCounted]): (=> Unit) => Runnable = {
    using(resources, null) _
  }

  protected def releasing(resource: RefCounted): (=> Unit) => Runnable = {
    using(null, resource) _
  }

  protected def releasing(resources: Seq[RefCounted]): (=> Unit) => Runnable = {
    using(null, resources) _
  }

  protected def retain(retainedResources: Seq[RefCounted]) = {
    if (retainedResources != null) {
      for (resource <- retainedResources) {
        resource.retain
      }
    }
  }

  protected def release(releasedResources: Seq[RefCounted]) = {
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

  private def using(retainedResource: RefCounted, releasedResource: RefCounted)(proc: => Unit): Runnable = {
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

  private def using(retainedResources: Seq[RefCounted], releasedResources: Seq[RefCounted])(proc: => Unit): Runnable = {
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
  override def startup() = {
    queue.retain
  }

  override def shutdown() = {
    queue << ^ {onShutdown}
    queue.release
  }

  protected def onShutdown() = {}
}

trait QueuedRefCounted extends Queued with ServiceRetainer {
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