/**
 * Copyright (C) 2011, FuseSource Corp.  All rights reserved.
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
 * limitations under the License.
 */
package org.fusesource.hawtdispatch.example

import java.io.{IOException}
import org.fusesource.hawtdispatch._

import java.nio.ByteBuffer
import java.nio.channels.{DatagramChannel, SelectionKey, ServerSocketChannel, SocketChannel}
import java.net.{SocketAddress, InetSocketAddress}
import java.util.LinkedList

/**
 * A udp echo server example.  Concurrently reads and writes
 * packets by using 2 dispatch queues.  Uses a custom dispatch
 * source to handle coalescing interaction events between
 * the sender and receiver queues.
 * 
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
object UdpEchoServer {

  var port=4444;

  def main(args:Array[String]):Unit = {
    run
  }

  def run() = {
    val server = new Server(port)
    server.start
    println("Press enter to shutdown.");
    System.in.read
    server.close
  }

  class Server(val port: Int) {

    val channel = DatagramChannel.open();
    channel.socket().bind(new InetSocketAddress(port));
    channel.configureBlocking(false);
    println("Listening on port: "+port);

    object receiver {
      // All mutable state in this object is modified while executing
      // on this queue
      val queue = createQueue("receive")

      private var outbound = 0
      private val outbound_max = 1024;

      val read_events = createSource(channel, SelectionKey.OP_READ, queue);
      read_events.onEvent {
        try {
          val buffer = ByteBuffer.allocate(1024);
          var address: SocketAddress = channel.receive(buffer);
          if( address!=null ) {
            buffer.flip;
            outbound += 1
            sender.outbound_events.merge((buffer, address))
            // stop receiving until the outbound is drained (aka: flow control)
            if ( outbound_max < outbound ) {
              read_events.suspend
            }
          }
        } catch {
          case e:IOException => close
        }
      }

      // outbound_ack_events is used to let the sender know when the sends complete
      val outbound_ack_events = createSource(EventAggregators.INTEGER_ADD, queue)
      outbound_ack_events.onEvent {
        outbound -= outbound_ack_events.getData()
        if(read_events.isSuspended)
          read_events.resume()
      }
      outbound_ack_events.resume();

    }

    object sender {
      // All mutable state in this object is modified while executing
      // on this queue
      val queue = createQueue("send")

      // pick up outbound events
      private val outbound = new LinkedList[(ByteBuffer, SocketAddress)]

      // outbound_events is an event bridge between the receiver and the sender event queues
      // It will merge multiple events from the receiver queue into 1 event that gets delivered
      // to the sender queue
      val outbound_events = createSource(new ListEventAggregator[(ByteBuffer, SocketAddress)], queue)
      outbound_events.onEvent {
        for( value <- outbound_events.getData() ) {
          outbound.add(value)
        }
        drainOutbound
      }
      outbound_events.resume();

      // We need to drain the list of outbound packets when socket reports it 
      // can be written to.
      val write_events = createSource(channel, SelectionKey.OP_WRITE, queue);
      write_events.onEvent(drainOutbound)

      def drainOutbound:Unit = try {
        while(!outbound.isEmpty) {
          val (buffer, address) = outbound.peek();
          channel.send(buffer, address)
          if(buffer.remaining()==0) {
            // Packet sent, let the receive know in case he stopped.
            receiver.outbound_ack_events.merge(1)
            outbound.poll()
          } else {
            // Could not complete the write, we may need
            // to resume the write source
            if(write_events.isSuspended)
              write_events.resume()
            return
          }
        }
        // Nothing left? then stop looking for write events
        if(!write_events.isSuspended)
          write_events.suspend
      } catch {
        case e:IOException => close
      }

    }

    def start() = {
      receiver.read_events.resume
    }

    def close() = {
      receiver.read_events.cancel
      sender.write_events.cancel
      channel.close
    }

  }

}
