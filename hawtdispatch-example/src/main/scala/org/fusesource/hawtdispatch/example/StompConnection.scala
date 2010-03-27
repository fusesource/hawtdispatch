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
import _root_.org.fusesource.hawtdispatch.example.StompBroker.{Producer, Consumer}
import java.nio.channels.SelectionKey._
import org.fusesource.hawtdispatch.ScalaSupport._

import java.net.{InetAddress, InetSocketAddress}

import buffer._
import AsciiBuffer._
import java.util.concurrent.atomic.AtomicLong
import java.nio.channels.{SocketChannel, ServerSocketChannel}
import java.io.{IOException}
import org.fusesource.hawtdispatch.example.Stomp.{Headers, Responses, Commands}
import collection.mutable.{HashMap}

/**
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
object StompConnection {
  val connectionCounter = new AtomicLong();
}
class StompConnection(val socket:SocketChannel, var router:Router[AsciiBuffer,Producer,Consumer]) extends Queued {

  import StompBroker._
  import StompConnection._

  socket.socket.setSendBufferSize(1024*64)
  socket.socket.setReceiveBufferSize(1024*64)

  val queue = createSerialQueue("connection:"+connectionCounter.incrementAndGet)
  queue.setTargetQueue(getRandomThreadQueue)

//    println("connected from: "+socket.socket.getRemoteSocketAddress)

  val wireFormat = new StompWireFormat()
  var outbound = new LinkedList[(StompFrame,Retained)]()
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

  def drain_outbound_data = wireFormat.drain_source(socket) {
    val node = outbound.poll
    if( node !=null ) {
      node match {
        case (frame,null)=>
          frame
        case (frame,retained)=> {
          retained.release
          frame
        }
      }
    } else {
      null
    }
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


  def send(frame:StompFrame, retained:Retained=null) = {
    if( outbound.size < 10000 ) {
      outbound.add((frame,null))
    } else {
      // we only start retaining once our outbound is full..
      // retaining will cause the producers to slow down until
      // we released
      if( retained !=null ) {
        retained.retain
      }
      outbound.add((frame,retained))
    }

    if( outbound.size == 1 ) {
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
//          println("got CONNECT")
        on_stomp_connect(headers)
      case StompFrame(Commands.SEND, headers, content) =>
//          println("got SEND")
        on_stomp_send(headers, content)
      case StompFrame(Commands.SUBSCRIBE, headers, content) =>
//          println("got SUBSCRIBE")
        on_stomp_subscribe(headers)
      case StompFrame(Commands.ACK, headers, content) =>
//          println("got ACK")
        // TODO:
      case StompFrame(Commands.DISCONNECT, headers, content) =>
//          println("got DISCONNECT")
        close
      case StompFrame(unknown, _, _) =>
        die("Unsupported STOMP command: "+unknown);
    }
  }

  def on_stomp_connect(headers:HeaderMap) = {
    println("connected on: "+Thread.currentThread.getName);
    send(StompFrame(Responses.CONNECTED))
  }

  var producerRoute:Route[AsciiBuffer, Producer, Consumer]=null

  def on_stomp_send(headers:HeaderMap, content:Buffer) = {
    headers.get(Headers.Send.DESTINATION) match {
      case Some(dest)=>
        // create the producer route...
        if( producerRoute==null || producerRoute.destination!= dest ) {

          // clean up the previous producer..
          if( producerRoute!=null ) {
            router.disconnect(producerRoute)
            producerRoute=null
          }

          val producer = new Producer() {
            override def setTargetQueue(value:DispatchQueue):Unit = ^{
              queue.setTargetQueue(value)
              write_source.setTargetQueue(queue);
              read_source.setTargetQueue(queue)
            } ->: queue
          }

          // don't process frames until we are connected..
          read_source.suspend
          router.connect(dest, queue, producer) {
            route:Route[AsciiBuffer, Producer, Consumer] =>
              read_source.resume
              producerRoute = route
              send_via_route(producerRoute, headers, content)
          }
        } else {
          // we can re-use the existing producer route
          send_via_route(producerRoute, headers, content)
        }
      case None=>
        die("destination not set.")
    }
  }

  def send_via_route(route:Route[AsciiBuffer, Producer, Consumer], headers:HeaderMap, content:Buffer) = {
    if( !route.targets.isEmpty ) {
      read_source.suspend
      var delivery = Delivery(headers, content)
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
    headers.get(Headers.Subscribe.DESTINATION) match {
      case Some(dest)=>
        if( consumer !=null ) {
          die("Only one subscription supported.")

        } else {
          consumer = new SimpleConsumer(dest, queue);
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
    send(StompFrame(Responses.ERROR, new HashMap(), ascii(msg)))
    ^ {
      close
    } ->: queue
  }

  class SimpleConsumer(val dest:AsciiBuffer, override val queue:DispatchQueue) extends Consumer {
    override def deliver(delivery:Delivery) = using(delivery) {
      send(StompFrame(Responses.MESSAGE, delivery.headers, delivery.content), delivery)
    } ->: queue
  }

}
