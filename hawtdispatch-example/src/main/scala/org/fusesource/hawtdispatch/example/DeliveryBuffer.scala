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

import _root_.java.util.LinkedList
import _root_.org.fusesource.hawtdispatch.ScalaSupport._
import _root_.org.fusesource.hawtdispatch.{EventAggregator, ScalaSupport, DispatchQueue}

/**
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
class DeliveryBuffer(var maxSize:Int=1024*32) {

  var deliveries = new LinkedList[Delivery]()
  private var size = 0
  var eventHandler: Runnable = null
  
  def full = size >= maxSize

  def drain = eventHandler.run

  def receive = deliveries.poll

  def isEmpty = deliveries.isEmpty

  def send(delivery:Delivery):Unit = {
    delivery.retain
    size += delivery.size
    deliveries.addLast(delivery)
    if( deliveries.size == 1 ) {
      drain
    }
  }

  def ack(delivery:Delivery) = {
    // When a message is delivered to the consumer, we release
    // used capacity in the outbound queue, and can drain the inbound
    // queue
    val wasBlocking = full
    size -= delivery.size
    delivery.release
    if( !isEmpty ) {
      drain
    }
  }

}

class DeliveryOverflowBuffer(val delivery_buffer:DeliveryBuffer) {

  private var overflow = new LinkedList[Delivery]()

  protected def drainOverflow:Unit = {
    while( !overflow.isEmpty && !full ) {
      val delivery = overflow.removeFirst
      delivery.release
      send_to_delivery_queue(delivery)
    }
  }

  def send(delivery:Delivery) = {
    if( full ) {
      // Deliveries in the overflow queue is remain acquired by us so that
      // producer that sent it to us gets flow controlled.
      delivery.retain
      overflow.addLast(delivery)
    } else {
      send_to_delivery_queue(delivery)
    }
  }

  protected def send_to_delivery_queue(value:Delivery) = {
    var delivery = Delivery(value)
    delivery.addReleaseWatcher(^{
      drainOverflow
    })
    delivery_buffer.send(delivery)
    delivery.release
  }

  def full = delivery_buffer.full

}

class DeliveryCreditBufferProtocol(val delivery_buffer:DeliveryBuffer, val queue:DispatchQueue) extends BaseRetained {

  var sessions = List[CreditServer]()

  var session_min_credits = 1024*4;
  var session_credit_capacity = 1024*32
  var session_max_credits = session_credit_capacity;

  queue.retain
  addReleaseWatcher(^{
    source.release
    queue.release
  })

  // use a event aggregating source to coalesce multiple events from the same thread.
  val source = createSource(ListEventAggregator[Delivery](), queue)
  source.setEventHandler(^{drain_source});
  source.resume

  def drain_source = {
    val deliveries = source.getData
    deliveries.foreach { delivery=>
      delivery_buffer.send(delivery)
      delivery.release
    }
  }


  class CreditServer(val producer_queue:DispatchQueue) {
    private var _capacity = 0

    def capacity(value:Int) = {
      val change = value - _capacity;
      _capacity = value;
      client.credit(change)
    }

    def drain(callback:Runnable) = {
      client.drain(callback)
    }

    val client = new CreditClient()

    class CreditClient() extends DeliveryOverflowBuffer(delivery_buffer) {

      producer_queue.retain
      val credit_adder = createSource(EventAggregator.INTEGER_ADD , producer_queue)
      credit_adder.setEventHandler(^{
        internal_credit(credit_adder.getData.intValue)
      });
      credit_adder.resume

      private var credits = 0;

      ///////////////////////////////////////////////////
      // These methods get called from the client/producer thread...
      ///////////////////////////////////////////////////
      def close = {
        credit_adder.release
        producer_queue.release
      }

      override def full = credits <= 0

      override protected def send_to_delivery_queue(value:Delivery) = {
        var delivery = Delivery(value)
        delivery.addReleaseWatcher(^{
          // This is called from the server/consumer thread
          credit_adder.merge(delivery.size);
        })
        internal_credit(-delivery.size)
        source.merge(delivery)
      }

      def internal_credit(value:Int) = {
        credits += value;
        if( credits <= 0 ) {
          credits = 0
        } else {
          drainOverflow
        }
      }

      ///////////////////////////////////////////////////
      // These methods get called from the server/consumer thread...
      ///////////////////////////////////////////////////
      def credit(value:Int) = ^{ internal_credit(value) } ->: producer_queue

      def drain(callback:Runnable) = {
        credits = 0
        if( callback!=null ) {
          queue << callback
        }
      }
    }
  }

  def session(queue:DispatchQueue) = {
    val session = new CreditServer(queue)
    sessions = session :: sessions
    session.capacity(session_max_credits)
    session.client
  }


}