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
 * limitations under the License.
 */
package org.fusesource.hawtdispatch.example

import java.io.{IOException}
import java.net.{InetSocketAddress}
import org.fusesource.hawtdispatch._

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

/**
 * A simple echo server example.
 * 
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
object EchoServer {

  var port=4444;

  def main(args:Array[String]):Unit = {
    run
  }

  def run() = {
    val server = new Server(port).start
    println("Press enter to shutdown.");
    System.in.read
    server.stop
  }

  class Server(val port: Int) {
    val channel = ServerSocketChannel.open();
    channel.socket().bind(new InetSocketAddress(port));
    channel.configureBlocking(false);

    val queue = createQueue("server")
    val accept_source = createSource(channel, SelectionKey.OP_ACCEPT, queue);
    accept_source.setEventHandler(^ {
      val socket = channel.accept();
      try {
        socket.configureBlocking(false);
        new Session(socket).start()
      } catch {
        case e: Exception =>
          socket.close
      }
    });

    println("Listening on port: "+port);

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
      println("Closed port: "+port);
    });
    
  }

  class Session(val channel: SocketChannel) {

    val buffer = ByteBuffer.allocate(1024);
    val queue = createQueue("session")
    val read_source = createSource(channel, SelectionKey.OP_READ, queue);
    val write_source = createSource(channel, SelectionKey.OP_WRITE, queue);
    val remote_address = channel.socket.getRemoteSocketAddress.toString

    def start() = {
      println("Accepted connection from: "+remote_address);
      read_source.resume
    }

    def close() = {
      read_source.release
      write_source.release
      queue.release
    }

    queue.setDisposer(^{
      channel.close
      println("Closed connection from: "+remote_address);
    })

    read_source.setEventHandler(^{
      try {
        if (channel.read(buffer) == -1) {
          close
        } else {
          buffer.flip;
          if (buffer.remaining > 0) {
            read_source.suspend
            write_source.resume
          } else {
            buffer.clear
          }
        }
      }
      catch {
        case e:IOException => close
      }
    })

    write_source.setEventHandler(^{
      try {
        channel.write(buffer)
        if (buffer.remaining == 0) {
          buffer.clear
          write_source.suspend
          read_source.resume
        }
      }
      catch {
        case e:IOException => close
      }
    })


  }

}
