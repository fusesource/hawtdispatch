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

import _root_.java.util.{LinkedList}
import buffer.AsciiBuffer
import org.fusesource.hawtdispatch.ScalaSupport._

import collection.mutable.{HashMap}
import collection.immutable.Queue

object StompQueue {
  val maxOutboundSize = 1024*1204*5
}

/**
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
class StompQueue(val destination:AsciiBuffer) extends Route with Consumer with Producer with BaseRetained {
  
  import StompQueue._;
  
  override val queue:DispatchQueue = createSerialQueue("queue:"+destination);
  queue.setTargetQueue(getRandomThreadQueue)
  addReleaseWatcher(^{
    queue.release
  })

  var inbound = new LinkedList[Delivery]()
  var outbound = new LinkedList[Delivery]()
  var outboundSize = 0;

  class ConsumerState(val consumer:Consumer) {
//    var dispatched = new LinkedList[Delivery]()
    var bound=true

    def deliver(value:Delivery):Unit = {
      val delivery = Delivery(value)
//      dispatched.addLast(delivery)
      delivery.addReleaseWatcher(^{
        ^{ completed(delivery) } ->:queue
      })
      consumer.deliver(delivery);
      delivery.release
    }

    def completed(delivery:Delivery) = {

      // Lets get back on the readyList if  we are still bound.
      if( bound ) {
        readyConsumers.addLast(this)
      }

      // When a message is delivered to the consumer, we release
      // used capacity in the outbound queue, and can drain the inbound
      // queue
      val wasBlocking = isBlockingProducers
      outboundSize -= delivery.size
      if( wasBlocking && !isBlockingProducers) {
        // draining the inbound will also trigger draining the outbound
        drainInbound
      } else {
        // lets just drain the out abound to give this consumer some
        // messages.
        drainOutbound
      }

    }

  }

  var allConsumers = Map[Consumer,ConsumerState]()
  val readyConsumers = new LinkedList[ConsumerState]()

  def connected(consumers:List[Consumer]) = bind(consumers)
  def bind(consumers:List[Consumer]) = retaining(consumers) {
      for ( consumer <- consumers ) {
        val cs = new ConsumerState(consumer)
        allConsumers += consumer->cs
        readyConsumers.addLast(cs)
      }
      drainOutbound
    } ->: queue

  def unbind(consumers:List[Consumer]) = releasing(consumers) {
      for ( consumer <- consumers ) {
        allConsumers.get(consumer) match {
          case Some(cs)=>
            cs.bound = false
            allConsumers -= consumer
            readyConsumers.remove(cs)
          case None=>
        }
      }
    } ->: queue

  def disconnected() = throw new RuntimeException("unsupported")

  def colocate(value:DispatchQueue):Unit = {
    if( value.getTargetQueue ne queue.getTargetQueue ) {
      println(queue.getLabel+" co-locating with: "+value.getLabel);
      this.queue.setTargetQueue(value.getTargetQueue)
    }
  }

  def deliver(delivery:Delivery) = using(delivery) {
    send(delivery)
  } ->: queue


  def send(delivery:Delivery=null):Unit = {
    // do we have the capacity to accept new messages??
    if( isBlockingProducers ) {
      // Stuff in the inbound queue is remains acquired by us so that
      // producer that sent it to use remains flow controlled.
      delivery.retain
      inbound.addLast(delivery)
    } else {
      outbound.add(delivery)
      outboundSize += delivery.size
      drainOutbound
    }
  }

  def isBlockingProducers = outboundSize >= maxOutboundSize

  def drainInbound = {
    while( !isBlockingProducers && !inbound.isEmpty) {
      val delivery = inbound.removeFirst
      delivery.release

      outbound.add(delivery)
      outboundSize += delivery.size
    }
    drainOutbound
  }

  def drainOutbound = {
    while( !readyConsumers.isEmpty && !outbound.isEmpty ) {
      val cs = readyConsumers.removeFirst
      val delivery = outbound.removeFirst
      cs.deliver(delivery)
    }
  }



}
