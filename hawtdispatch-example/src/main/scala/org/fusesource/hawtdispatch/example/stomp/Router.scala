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
package org.fusesource.hawtdispatch.example.stomp

import _root_.java.util.concurrent.atomic.AtomicLong
import _root_.org.fusesource.hawtdispatch._
import ScalaDispatch._
import java.util.HashMap
import collection.JavaConversions

import org.fusesource.hawtbuf._
import Buffer._


object Router {
  val TOPIC_PREFIX = new AsciiBuffer("/topic/")
  val QUEUE_PREFIX = new AsciiBuffer("/topic/")
}
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
class Router(var queue:DispatchQueue) {
  import Router._
  
  trait DestinationNode {
    var targets = List[Consumer]()
    var routes = List[ProducerRoute]()

    def on_bind(x:List[Consumer]):Unit
    def on_unbind(x:List[Consumer]):Boolean
    def on_connect(route:ProducerRoute):Unit
    def on_disconnect(route:ProducerRoute):Boolean = {
      routes = routes.filterNot({r=> route==r})
      route.disconnected()
      routes == Nil && targets == Nil
    }
  }

  class TopicDestinationNode extends DestinationNode {
    def on_bind(x:List[Consumer]) =  {
      targets = x ::: targets
      routes.foreach({r=>
        r.bind(x)
      })
    }

    def on_unbind(x:List[Consumer]):Boolean = {
      targets = targets.filterNot({t=>x.contains(t)})
      routes.foreach({r=>
        r.unbind(x)
      })
      routes == Nil && targets == Nil
    }

    def on_connect(route:ProducerRoute) = {
      routes = route :: routes
      route.connected(targets)
    }
  }

  class QueueDestinationNode(destination:AsciiBuffer) extends DestinationNode {
    val queue = new StompQueue(destination)

    def on_bind(x:List[Consumer]) =  {
      targets = x ::: targets
      queue.bind(x)
    }

    def on_unbind(x:List[Consumer]):Boolean = {
      targets = targets.filterNot({t=>x.contains(t)})
      queue.unbind(x)
      routes == Nil && targets == Nil
    }

    def on_connect(route:ProducerRoute) = {
      routes = route :: routes
      route.connected(queue :: Nil)
    }
  }

  var destinations = new HashMap[AsciiBuffer, DestinationNode]()

  private def get(destination:AsciiBuffer):DestinationNode = {
    var result = destinations.get(destination)
    if( result ==null ) {
      if( isTopic(destination) ) {
        result = new TopicDestinationNode
      } else {
        result = new QueueDestinationNode(destination)
      }
      destinations.put(destination, result)
    }
    result
  }

  def bind(destination:AsciiBuffer, targets:List[Consumer]) = retaining(targets) {
      get(destination).on_bind(targets)
    } >>: queue

  def unbind(destination:AsciiBuffer, targets:List[Consumer]) = releasing(targets) {
      if( get(destination).on_unbind(targets) ) {
        destinations.remove(destination)
      }
    } >>: queue

  def connect(destination:AsciiBuffer, routeQueue:DispatchQueue, producer:Producer)(completed: (ProducerRoute)=>Unit) = {
    val route = new ProducerRoute(destination, routeQueue, producer) {
      override def on_connected = {
        completed(this);
      }
    }
    ^ {
      get(destination).on_connect(route)
    } >>: queue
  }

  def isTopic(destination:AsciiBuffer) = destination.startsWith(TOPIC_PREFIX)
  def isQueue(destination:AsciiBuffer) = !isTopic(destination)

  def disconnect(route:ProducerRoute) = releasing(route) {
      get(route.destination).on_disconnect(route)
    } >>: queue


   def each(proc:(AsciiBuffer, DestinationNode)=>Unit) = {
     import JavaConversions._;
     for( (destination, node) <- destinations ) {
        proc(destination, node)
     }
   }

}

trait Route extends Retained {

  val destination:AsciiBuffer
  val queue:DispatchQueue
  val metric = new AtomicLong();

  def connected(targets:List[Consumer]):Unit
  def bind(targets:List[Consumer]):Unit
  def unbind(targets:List[Consumer]):Unit
  def disconnected():Unit

}

class ProducerRoute(val destination:AsciiBuffer, val queue:DispatchQueue, val producer:Producer) extends BaseRetained with Route {


  // Retain the queue while we are retained.
  queue.retain
  setDisposer(^{
    queue.release
  })

  var targets = List[ConsumerSession]()

  def connected(targets:List[Consumer]) = retaining(targets) {
    internal_bind(targets)
    on_connected
  } >>: queue

  def bind(targets:List[Consumer]) = retaining(targets) {
    internal_bind(targets)
  } >>: queue

  private def internal_bind(values:List[Consumer]) = {
    values.foreach{ x=>
      targets = x.open_session(queue) :: targets
    }
  }

  def unbind(targets:List[Consumer]) = releasing(targets) {
    this.targets = this.targets.filterNot { x=>
      val rc = targets.contains(x.consumer)
      if( rc ) {
        x.close
      }
      rc
    }
  } >>: queue

  def disconnected() = ^ {
    this.targets.foreach { x=>
      x.close
      x.consumer.release
    }    
  } >>: queue

  protected def on_connected = {}
  protected def on_disconnected = {}

}
