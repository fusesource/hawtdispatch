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
 */
package org.fusesource.hawtdispatch.example

import java.util.HashMap
import org.fusesource.hawtdispatch._
import org.fusesource.hawtdispatch.scala._

/**
 * Provides a non-blocking concurrent producer to consumer
 * routing implementation.
 *
 * Producers create a route object for each destination
 * they will be producing to.  Once the route is
 * connected to the router, the producer can use
 * the route.targets list without synchronization to
 * get the current set of consumers that are bound
 * to the destination. 
 *
 */
class Router[D, T <: Retained](var queue:TaskQueue) extends Queued {

  class DestinationNode {
    var targets = List[T]()
    var routes = List[Route[D,T]]()

    def on_bind(x:List[T]) =  {
      targets = x ::: targets
      routes.foreach({r=>
        r.bind(x)
      })
    }

    def on_unbind(x:List[T]):Boolean = {
      targets = targets.filterNot({t=>x.contains(t)})
      routes.foreach({r=>
        r.unbind(x)
      })
      routes == Nil && targets == Nil
    }

    def on_connect(route:Route[D,T]) = {
      routes = route :: routes
      route.connected(targets)
    }

    def on_disconnect(route:Route[D,T]):Boolean = {
      routes = routes.filterNot({r=> route==r})
      route.disconnected()
      routes == Nil && targets == Nil
    }
  }

  private var destinations = new HashMap[D, DestinationNode]()

  private def get(destination:D) = {
    var result = destinations.get(destination)
    if( result ==null ) {
      result = new DestinationNode
      destinations.put(destination, result)
    }
    result
  }

  def bind(destination:D, targets:List[T]) = retaining(targets) {
      get(destination).on_bind(targets)
    } ->: queue

  def unbind(destination:D, targets:List[T]) = releasing(targets) {
      if( get(destination).on_unbind(targets) ) {
        destinations.remove(destination)
      }
    } ->: queue

  def connect(destination:D, routeQueue:TaskQueue)(completed: (Route[D,T])=>Unit) = {
    val route = new Route[D,T](destination, routeQueue) {
      override def on_connected = {
        completed(this);
      }
    }
    ^ {
      get(destination).on_connect(route)
    } ->: queue
  }

  def disconnect(route:Route[D,T]) = releasing(route) {
      get(route.destination).on_disconnect(route)
    } ->: queue

}


class Route[D, T <: Retained ](val destination:D, var queue:TaskQueue) extends QueuedRetained {

  var targets = List[T]()

  def connected(targets:List[T]) = retaining(targets) {
      this.targets = this.targets ::: targets
      on_connected
    } ->: queue

  def bind(targets:List[T]) = retaining(targets) {
      this.targets = this.targets ::: targets
    } ->: queue

  def unbind(targets:List[T]) = releasing(targets) {
      this.targets = this.targets.filterNot {
        t=>targets.contains(t)
      }
    } ->: queue

  def disconnected() = ^ {
      release(targets)
      targets = Nil
      on_disconnected
    } ->: queue

  protected def on_connected = {}
  protected def on_disconnected = {}
}