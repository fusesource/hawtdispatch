/**
 * Copyright (C) 2010, Progress Software Corporation and/or its
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
 * limitations under the License.
 */
package org.fusesource.hawtdispatch

import org.scalatest._
import junit.JUnitRunner
import org.junit.runner.RunWith
import org.scalatest.matchers.ShouldMatchers
import java.util.concurrent.atomic.AtomicBoolean
import java.net.InetSocketAddress
import java.nio.channels.{SocketChannel, ServerSocketChannel}
import java.nio.ByteBuffer
import org.fusesource.hawtdispatch._
import util.continuations._

/**
 * <p>
 * </p>
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
@RunWith(classOf[JUnitRunner])
class HawtSocketChannelTest extends FunSuite with ShouldMatchers {

  def noop = shift {  k: (Unit=>Unit) => k() }

  test("Echo Server Test") {

    // Lets implement a simple non blocking echo server..
    var server_shutdown = new AtomicBoolean()
    val server = HawtServerSocketChannel({
      val channel = ServerSocketChannel.open();
      channel.socket().bind(new InetSocketAddress(0));
      channel
    })


    // This handles a socket connection..
    def service(channel:SocketChannel) = {

      val socket = HawtSocketChannel(channel)
      val buffer = ByteBuffer.allocate(1024*4)

      def close(reason:Exception) = {
        if( reason!=null ) {
          println("socket failure: "+reason)
        }
        socket.cancel
        socket.channel.close
      }

      // client processing is done on hawtdispatch queue
      socket.queue {
        reset {
          while (!server_shutdown.get() && socket.channel.isOpen ) {
            buffer.clear
            val rc = socket.read(buffer)
            if( !rc.isDefined ) {
              buffer.flip
              val rc = socket.write(buffer)
              if( rc.isDefined ) {
                close(rc.get)
              } else {
                noop
              }
            } else {
              close(rc.get)
            }
          }
        }
      }

    }

    // server accept processing is done on hawtdispatch queue
    server.queue {
      reset {
        while (!server_shutdown.get() && server.channel.isOpen) {
          val rc = server.accept
          if( rc.isLeft ) {
            service(rc.left.get)
            noop
          } else {
            println("accept failed: "+rc.right.get)
            server.cancel
            server.channel.close
          }
        }
      }
    }


    //
    // Use a simple blocking client to test the non blocking echo server..
    val client = SocketChannel.open()
    client.connect(new InetSocketAddress("localhost", server.channel.socket.getLocalPort))
    client.write(ByteBuffer.wrap("Hello".getBytes));
    val buffer = ByteBuffer.allocate(1024);
    client.read(buffer) should equal(5)
    client.close

    // Shutdown the server
    server_shutdown.set(true)
    reset {
      server.cancel
    }

  }


}
