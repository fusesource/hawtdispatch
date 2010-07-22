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

/**
 * <p>
 * Contains several helper method for working with retained objects
 * and callbacks.
 * </p>
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
object ScalaDispatchHelpers {

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

