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
import java.nio._
import java.nio.channels._
import java.net._
import java.io.IOException
import org.fusesource.hawtdispatch._
import java.util.concurrent.atomic.AtomicInteger;

/**
 * <p>
 * </p>
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
@RunWith(classOf[JUnitRunner])
class SocketTest extends FunSuite with ShouldMatchers {

  test("Socket Disconnect Event") {

    val connections = new AtomicInteger(0);

    class Server() {
      val channel = ServerSocketChannel.open();
      channel.socket().bind(new InetSocketAddress(0));
      channel.configureBlocking(false);

      val queue = createQueue("server")
      val accept_source = createSource(channel, SelectionKey.OP_ACCEPT, queue);
      accept_source.setEventHandler(^ {
        val socket = channel.accept();
        try {
          new Session(socket).start()
        } catch {
          case e: Exception => socket.close
        }
      });

      def start() = {
        accept_source.resume
        this
      }

      def stop() = {
        accept_source.release
        queue.release
      }

      accept_source.setDisposer(^{
        channel.close();
        channel.isOpen
      });

      
      def port = channel.socket.getLocalPort

    }

    class Session(val channel: SocketChannel) {
      channel.configureBlocking(false);
      channel.socket.setSoLinger(true, 0)

      val buffer = ByteBuffer.allocate(1024);
      val queue = createQueue("session")
      val read_source = createSource(channel, SelectionKey.OP_READ, queue);
      var closed = false

      read_source.setEventHandler(^{
        try {
          buffer.clear
          if( !channel.isConnected ) {
            close
          } else if (channel.read(buffer) == -1) {
            if( !closed ) {
              close
            }
          }
        } catch {
          case e:IOException =>
            if( !closed ) {
              close
            }
        }
      })

      connections.incrementAndGet
      def start() = read_source.resume

      read_source.setCancelHandler(^{ if( !closed ) {
        close()
      } })

      def close() = {
        if( !closed ) {
          read_source.release
          closed = true;
        }
      }

      read_source.setDisposer(^{
        connections.decrementAndGet
        channel.close
      })

    }


    def connections_should_equal(value:Int):Unit = {
      for( i <- 0 until 20 ) {
        if( connections.get==value ) {
          return;
        }
        Thread.sleep(100);
      }
      connections.get should equal(value)
    }

    val server = new Server()
    server.start

    for( i <- 0 until 20 ) {
      connections_should_equal(0)
      val socket = new Socket("localhost", server.port);
      socket.setSoLinger(true, 0)
      connections_should_equal(1)
      socket.close
      connections_should_equal(0)
    }

  }

  
}
