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
import _root_.org.fusesource.hawtdispatch.ScalaSupport
import java.nio.channels.SelectionKey._
import org.fusesource.hawtdispatch.ScalaSupport._

import buffer._
import AsciiBuffer._
import java.util.concurrent.atomic.AtomicLong
import java.nio.channels.{SocketChannel}
import java.io.{IOException}
import org.fusesource.hawtdispatch.example.Stomp.{Headers, Responses, Commands}
import collection.mutable.{HashMap}

/**
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
object StompConnection {
  val connectionCounter = new AtomicLong();
  var bufferSize = 1024*64
  var maxOutboundSize = 100;
}
class StompConnection(val socket:SocketChannel, var router:Router) {

  import Delivery._
  import StompConnection._

  socket.socket.setSendBufferSize(bufferSize)
  socket.socket.setReceiveBufferSize(bufferSize)

  val queue = createSerialQueue("connection:"+connectionCounter.incrementAndGet)
  queue.setTargetQueue(getRandomThreadQueue)

//    println("connected from: "+socket.socket.getRemoteSocketAddress)

  val wireFormat = new StompWireFormat()


  val outboundChannel  = new DeliveryBuffer
  var outbound = new LinkedList[StompFrame]()

  var closed = false;
  var consumer:SimpleConsumer = null

  val write_source = createSource(socket, OP_WRITE, queue);
  val read_source = createSource(socket, OP_READ, queue);

  queue.addReleaseWatcher(^{
    socket.close();
  })

  read_source.setEventHandler(^{
    try {
      wireFormat.drain_socket(socket) {
        frame:StompFrame=>
          on_frame(frame)
          read_source.isSuspended
      }
    } catch {
      case e:IOException =>
        // The peer disconnected..
        close
    }
  });

  read_source.resume();

  def drain_outbound_data = wireFormat.drain_source(socket) { poll_outbound }

  def poll_outbound = {
    var rc = outbound.poll
    if( rc==null ) {
      val delivery = outboundChannel.receive
      if( delivery!=null ) {
        rc = StompFrame(Responses.MESSAGE, delivery.headers, delivery.content)
        outboundChannel.ack(delivery)
      }
    }
    rc
  }

  write_source.setEventHandler(^{
    try {
      if( drain_outbound_data ) {
        write_source.suspend
      }
    } catch {
      case e:IOException=>
        close // The peer must have closed on us..
    }
  });


  def send(frame:StompFrame) = {
    outbound.add(frame)
    if( outbound.size == 1 && outboundChannel.isEmpty ) {
      write_source.resume
    }
  }

  outboundChannel.eventHandler = ^{
    if( outbound.isEmpty && outboundChannel.deliveries.size==1 ) {
      write_source.resume
    }
  }

  def close = {
    if( !closed ) {
      closed=true;
      if( producerRoute!=null ) {
        router.disconnect(producerRoute)
        producerRoute=null
      }
      if( consumer!=null ) {
        router.unbind(consumer.dest, consumer::Nil)
        consumer=null
      }
      write_source.cancel
      write_source.release
      read_source.cancel
      read_source.release
      queue.release
    }
  }

  def on_frame(frame:StompFrame) = {
    frame match {
      case StompFrame(Commands.CONNECT, headers, _) =>
        on_stomp_connect(headers)
      case StompFrame(Commands.SEND, headers, content) =>
        on_stomp_send(Delivery(frame))
      case StompFrame(Commands.SUBSCRIBE, headers, content) =>
        on_stomp_subscribe(headers)
      case StompFrame(Commands.ACK, headers, content) =>
        // TODO:
      case StompFrame(Commands.DISCONNECT, headers, content) =>
        close
      case StompFrame(unknown, _, _) =>
        die("Unsupported STOMP command: "+unknown);
    }
  }

  def on_stomp_connect(headers:HeaderMap) = {
    println("connected on: "+Thread.currentThread.getName);
    send(StompFrame(Responses.CONNECTED))
  }

  var producerRoute:ProducerRoute=null


  def get(headers:HeaderMap, name:AsciiBuffer):Option[AsciiBuffer] = {
    val i = headers.iterator
    while( i.hasNext ) {
      val entry = i.next
      if( entry._1 == name ) {
        return Some(entry._2)
      }
    }
    None
  }

  def on_stomp_send(delivery:Delivery) = {
    get(delivery.headers, Headers.Send.DESTINATION) match {
      case Some(dest)=>
        // create the producer route...
        if( producerRoute==null || producerRoute.destination!= dest ) {

          // clean up the previous producer..
          if( producerRoute!=null ) {
            router.disconnect(producerRoute)
            producerRoute=null
          }

          val producer = new Producer() {
            override def collocate(value:DispatchQueue):Unit = ^{
              if( value.getTargetQueue ne queue.getTargetQueue ) {
                println("sender on "+queue.getLabel+" co-locating with: "+value.getLabel);
                queue.setTargetQueue(value.getTargetQueue)
                write_source.setTargetQueue(queue);
                read_source.setTargetQueue(queue)
              }

            } ->: queue
          }

          // don't process frames until we are connected..
          read_source.suspend
          router.connect(dest, queue, producer) {
            (route) =>
              read_source.resume
              producerRoute = route
              send_via_route(producerRoute, delivery)
          }
        } else {
          // we can re-use the existing producer route
          send_via_route(producerRoute, delivery)
        }
      case None=>
        die("destination not set.")
    }
  }

  def send_via_route(route:ProducerRoute, delivery:Delivery) = {
    if( !route.targets.isEmpty ) {
      read_source.suspend
      delivery.addReleaseWatcher(^{
        read_source.resume
      })
      route.targets.foreach(consumer=>{
        consumer.deliver(delivery)
      })
      delivery.release;
    }
  }

  def on_stomp_subscribe(headers:HeaderMap) = {
    println("Consumer on "+Thread.currentThread.getName)
    get(headers, Headers.Subscribe.DESTINATION) match {
      case Some(dest)=>
        if( consumer !=null ) {
          die("Only one subscription supported.")

        } else {
          consumer = new SimpleConsumer(dest);
          router.bind(dest, consumer :: Nil)
          consumer.release
        }
      case None=>
        die("destination not set.")
    }

  }

  private def die(msg:String) = {
    println("Shutting connection down due to: "+msg)
    read_source.suspend
    send(StompFrame(Responses.ERROR, new LinkedList(), ascii(msg)))
    ^ {
      close
    } ->: queue
  }

  class SimpleConsumer(val dest:AsciiBuffer) extends Consumer with BaseRetained {

    val queue:DispatchQueue = StompConnection.this.queue
    addReleaseWatcher(^{
      queue.release
    })

    def open_session = new ConsumerSession {
      val consumer = SimpleConsumer.this
      val deliveryQueue = new DeliveryOverflowBuffer(outboundChannel)
      retain

      def deliver(delivery:Delivery) = using(delivery) {
        deliveryQueue.send(delivery)
      } ->: queue

      def close = {
        release
      }
    }
    
  }
    
}
