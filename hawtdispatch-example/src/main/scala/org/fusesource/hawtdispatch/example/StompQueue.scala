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


  val channel  = new Channel

  class ConsumerState(val consumer:Consumer) {
    var bound=true

    def deliver(value:Delivery):Unit = {
      val delivery = Delivery(value)
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
      channel.ack(delivery)
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
      channel.eventHandler.run
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

  def collocate(value:DispatchQueue):Unit = {
    if( value.getTargetQueue ne queue.getTargetQueue ) {
      println(queue.getLabel+" co-locating with: "+value.getLabel);
      this.queue.setTargetQueue(value.getTargetQueue)
    }
  }

  def deliver(delivery:Delivery) = using(delivery) {
    send(delivery)
  } ->: queue


  def send(delivery:Delivery=null):Unit = {
    channel.send(delivery)
  }

  channel.eventHandler = ^{
    while( !readyConsumers.isEmpty && !channel.isEmpty ) {
      val cs = readyConsumers.removeFirst
      val delivery = channel.receive
      cs.deliver(delivery)
    }
  }


}
