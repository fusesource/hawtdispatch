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

import java.nio.channels.SelectionKey._
import org.fusesource.hawtdispatch.ScalaSupport._

import java.net.{InetAddress, InetSocketAddress}

import java.util.{LinkedList}
import buffer._
import AsciiBuffer._
import org.fusesource.hawtdispatch.example.stomp.Stomp._
import org.fusesource.hawtdispatch.example.{Router, Route}
import collection.mutable.HashMap
import java.util.concurrent.atomic.AtomicLong
import java.nio.channels.{ClosedChannelException, SocketChannel, ServerSocketChannel}
import java.io.{IOException, EOFException}

object StompBroker {
  def main(args:Array[String]) = {
    println("Starting stomp broker...")
    val broker = new StompBroker();
    println("Startup complete.")
    System.in.read
    println("Shutting down...")
    broker.close
    println("Shutdown complete.")
  }
}
class StompBroker extends Queued {

  type HeaderMap = collection.mutable.Map[AsciiBuffer, AsciiBuffer]

  trait Consumer extends QueuedRetained {
    def deliver(headers:HeaderMap, content:Buffer)
  }

  val router = new Router[AsciiBuffer,Consumer](createSerialQueue("router"))

  val queue = createSerialQueue("broker")

    // Create the nio server socket...
  val channel = ServerSocketChannel.open();
  channel.configureBlocking(false);
  channel.socket().bind(address("0.0.0.0", 61613), 10);

  def address(host: String, port: Int): InetSocketAddress = {
    return new InetSocketAddress(ip(host), port)
  }

  def ip(host: String): InetAddress = {
    return InetAddress.getByName(host)
  }

  // Create a source attached to the server socket to deal with new connections...
  val accept_source = createSource(channel, OP_ACCEPT, queue);
  accept_source.setEventHandler(^{
    var socket = channel.accept();
    try {
      socket.configureBlocking(false);
      socket.socket.setSoLinger(true,0);
      var connection = new StompConnection(socket)
    } catch {
      case e:Exception=>
        socket.close
    }
  });

  accept_source.setCancelHandler(^{
    channel.close();
  });

  accept_source.resume();

  def close = {
    accept_source.cancel
    queue.release
  }

  object StompConnection {
    val connectionCounter = new AtomicLong();
  }
  class StompConnection(val socket:SocketChannel) extends Queued {
    import StompConnection._

    val queue = createSerialQueue("connection:"+connectionCounter.incrementAndGet)

//    println("connected from: "+socket.socket.getRemoteSocketAddress)


    val wireFormat = new StompWireFormat()
    val outbound = new LinkedList[StompFrame]()
    var closed = false;

    def send(frame:StompFrame) = {
      outbound.addLast(frame);
      if( outbound.size == 1 ) {
        write_source.resume
      }
    }
    val write_source = createSource(socket, OP_WRITE, queue);
    write_source.setEventHandler(^{
      try {

        val drained = wireFormat.drain_to_socket(socket) {
          outbound.poll
        }
        // Once drained, we don't need write events..
        if( drained ) {
          write_source.suspend
        }

      } catch {
        case e:IOException=>
          // The peer closed on us..
          close
      }

    });

    val read_source = createSource(socket, OP_READ, queue);
    read_source.setEventHandler(^{
      try {
        wireFormat.read_socket(socket) {
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
    
    queue.addReleaseWatcher(^{
      socket.close();
    })
    read_source.resume();


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
      send(StompFrame(Responses.CONNECTED))
    }

    class Producer(var sendRoute:Route[AsciiBuffer,Consumer]) extends QueuedRetained {
      override val queue = StompConnection.this.queue
      def send(headers:HeaderMap, content:Buffer) = ^ {
      } ->: queue
    }

    var producerRoute:Route[AsciiBuffer, Consumer]=null

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

            // don't process frames until we are connected..
            read_source.suspend
            router.connect(dest, queue) {
              route:Route[AsciiBuffer, Consumer] =>
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

    def send_via_route(route:Route[AsciiBuffer, Consumer], headers:HeaderMap, content:Buffer) = {
      route.targets.foreach(consumer=>{
        consumer.deliver(headers, content)
      })
    }

    class SimpleConsumer(val dest:AsciiBuffer, override val queue:DispatchQueue) extends Consumer {
      override def deliver(headers:HeaderMap, content:Buffer) = ^ {
        send(StompFrame(Responses.MESSAGE, headers, content))
      } ->: queue
    }

    var consumer:SimpleConsumer = null

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


  }

}


