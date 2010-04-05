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

/**
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
class Channel(var maxOutboundSize:Int=1024*32) {

  var inbound = new LinkedList[Delivery]()
  var outbound = new LinkedList[Delivery]()
  var outboundSize = 0
  var eventHandler: Runnable = null
  
  def isBlockingProducers = outboundSize >= maxOutboundSize

  def send(delivery:Delivery):Unit = {
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


  private def drainInbound = {
    while( !isBlockingProducers && !inbound.isEmpty) {
      val delivery = inbound.removeFirst
      delivery.release

      outbound.add(delivery)
      outboundSize += delivery.size
    }
    drainOutbound
  }

  private def drainOutbound = eventHandler.run

  def receive = outbound.poll

  def isEmpty = outbound.isEmpty

  def size = outbound.size

  def ack(delivery:Delivery) = {
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