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

import _root_.java.util.{LinkedList}
import buffer.AsciiBuffer
import _root_.org.fusesource.hawtdispatch._
import ScalaDispatch._

import collection.mutable.{HashMap}
import collection.immutable.Queue

object StompQueue {
  val maxOutboundSize = 1024*1204*5
}

/**
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
class StompQueue(val destination:AsciiBuffer) extends BaseRetained with Route with Consumer with Producer {
  
  import StompQueue._;
  
  override val queue:DispatchQueue = createQueue("queue:"+destination);
  queue.setTargetQueue(getRandomThreadQueue)
  setDisposer(^{
    queue.release
  })


  val delivery_buffer  = new DeliveryBuffer

  class ConsumerState(val consumer:ConsumerSession) {
    var bound=true

    def deliver(value:Delivery):Unit = {
      val delivery = Delivery(value)
      delivery.setDisposer(^{
        ^{ completed(value) } ->:queue
      })
      consumer.deliver(delivery);
      delivery.release
    }

    def completed(delivery:Delivery) = {
      // Lets get back on the readyList if  we are still bound.
      if( bound ) {
        readyConsumers.addLast(this)
      }
      delivery_buffer.ack(delivery)
    }
  }

  var allConsumers = Map[Consumer,ConsumerState]()
  val readyConsumers = new LinkedList[ConsumerState]()

  def connected(consumers:List[Consumer]) = bind(consumers)
  def bind(consumers:List[Consumer]) = retaining(consumers) {
      for ( consumer <- consumers ) {
        val cs = new ConsumerState(consumer.open_session(queue))
        allConsumers += consumer->cs
        readyConsumers.addLast(cs)
      }
      delivery_buffer.eventHandler.run
    } ->: queue

  def unbind(consumers:List[Consumer]) = releasing(consumers) {
      for ( consumer <- consumers ) {
        allConsumers.get(consumer) match {
          case Some(cs)=>
            cs.bound = false
            cs.consumer.close
            allConsumers -= consumer
            readyConsumers.remove(cs)
          case None=>
        }
      }
    } ->: queue

  def disconnected() = throw new RuntimeException("unsupported")

  def collocate(value:DispatchQueue):Unit = {
    if( value.getTargetQueue ne queue.getTargetQueue ) {
      println(queue.getLabel+" co-locating with: "+value.getLabel);
      this.queue.setTargetQueue(value.getTargetQueue)
    }
  }


  delivery_buffer.eventHandler = ^{
    while( !readyConsumers.isEmpty && !delivery_buffer.isEmpty ) {
      val cs = readyConsumers.removeFirst
      val delivery = delivery_buffer.receive
      cs.deliver(delivery)
    }
  }


  val deliveryQueue = new DeliveryCreditBufferProtocol(delivery_buffer, queue)
  def open_session(producer_queue:DispatchQueue) = new ConsumerSession {
    val session = deliveryQueue.session(producer_queue)
    val consumer = StompQueue.this
    retain

    def deliver(delivery:Delivery) = session.send(delivery)
    def close = {
      session.close
      release
    }
  }

//  def open_session(producer_queue:DispatchQueue) = new ConsumerSession {
//    val consumer = StompQueue.this
//    val deliveryQueue = new DeliveryOverflowBuffer(delivery_buffer)
//    retain
//
//    def deliver(delivery:Delivery) = using(delivery) {
//      deliveryQueue.send(delivery)
//    } ->: queue
//
//    def close = {
//      release
//    }
//  }

  
}
