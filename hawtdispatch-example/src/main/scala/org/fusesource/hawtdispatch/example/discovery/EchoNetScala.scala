/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.fusesource.hawtdispatch.example.discovery


import _root_.java.io.{EOFException, ByteArrayOutputStream}
import _root_.java.net.{ConnectException, InetSocketAddress, URI}
import _root_.java.util.concurrent.TimeUnit


import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import org.fusesource.hawtdispatch.ScalaSupport._

/**
 * An example of a networks of servers which advertise connection information to each other.
 */
object EchoNetScala {

  def main(args:Array[String]):Unit = {
    run
  }
  
  def run() = {
    val a = new Server(4444).start();
    val b = new Server(5555).start();
    val c = new Server(6666).start();

    Thread.sleep(200);

    a.connect(3333);
    a.connect(b);
    b.connect(c);
    System.in.read
  }
  
  class Server(val port: Int) {
    val me = URI.create("conn://localhost:" + port);
    val serverChannel = ServerSocketChannel.open();
    serverChannel.socket().bind(new InetSocketAddress(port));
    serverChannel.configureBlocking(false);

    var seen = List[URI]()
    val queue = createSerialQueue(me.toString)
    val accept_source = createSource(serverChannel, SelectionKey.OP_ACCEPT, queue);
    accept_source.setEventHandler(^ {

      // we are a server

      // when you are a server, we must first listen for the
      // address of the client before sending data.

      // once they send us their address, we will send our
      // full list of known addresses, followed by our own
      // address to signal that we are done.

      // Afterward we will only pulls our heartbeat
      val client = serverChannel.accept();
      try {

        val address = client.socket.getRemoteSocketAddress.asInstanceOf[InetSocketAddress]
        trace("accept " + address.getPort());
        client.configureBlocking(false);

        // Server sessions start by reading the client's greeting
        val session = new Session(this, client, address)
        session.start_read_greeting

      } catch {
        case e: Exception =>
          client.close
      }

    });
    accept_source.setCancelHandler(^ {
      serverChannel.close();
    });
    trace("Listening");

    def start() = {
      accept_source.resume
      this
    }

    def stop() = {
      accept_source.suspend
    }

    def close() = {
      accept_source.release
      queue.release
    }

    def connect(s: Server):Unit = {
      connect(s.port);
    }

    def connect(port: Int):Unit = {
      connect(URI.create("conn://localhost:" + port));
    }

    def connect(uri: URI): Unit = ^{
      if ( me.equals(uri) || seen.contains(uri) )
        return;

      val port = uri.getPort();
      val host = uri.getHost();

      trace("open " + uri);

      val socketChannel = SocketChannel.open();
      socketChannel.configureBlocking(false);

      val address = new InetSocketAddress(host, port);

      socketChannel.connect(address);

      val connect_source = createSource(socketChannel, SelectionKey.OP_CONNECT, queue);
      connect_source.setEventHandler(^ {
        connect_source.release
        try {
          socketChannel.finishConnect
          trace("connected " + uri);
          val session = new Session(this, socketChannel, address, uri)
          session.start_write_greeting
        }
        catch {
          case e:ConnectException =>
            trace("connect to "+uri+" FAILED.");
        }
      })
      connect_source.resume
      seen = uri :: seen;

    } ->: queue

    def trace(str: String) {
      println(String.format("%5d       - %s", new Integer(port), str));
    }

  }

  class Session(val server:Server, val channel: SocketChannel, val address: InetSocketAddress, val uri: URI) {

    def this(server:Server, channel: SocketChannel, address: InetSocketAddress) = {
      this (server, channel, address, URI.create("conn://" + address.getHostName() + ":" + address.getPort()))
    }

    val read_buffer = ByteBuffer.allocate(1024);

    val queue = createSerialQueue(uri.toString)
    val read_source = createSource(channel, SelectionKey.OP_READ, queue);
    val write_source = createSource(channel, SelectionKey.OP_WRITE, queue);
    val seen = server.seen

    def start_read_greeting = {
      read_source.setEventHandler(read_greeting)
      read_source.resume
    }


    def read_greeting = ^{

      val message = read_frame
      if (message!=null) {
        // stop looking for read events..
        read_source.suspend
        val uri = URI.create(message);
        trace("welcome");

        // Send them our seen uris..
        var list:List[Any] = seen.filterNot(x=> x==server.me || x==uri )
        list = list ::: List("end");

        start_write_data(list, ^{
          start_read_hearbeat
        })
      }
    }

    def start_write_greeting = {
      trace("hello");
      start_write_data(server.me::Nil, ^{
        start_read_server_listings
      })
    }

    def start_read_server_listings = {
      read_source.setEventHandler(read_server_listings)
      read_source.resume
    }

    var listed = List[URI]()

    def read_server_listings = ^ {
      val message = read_frame
      if (message!=null) {
        if( message != "end" ) {
          val uri: URI = URI.create(message)
          listed = uri :: listed;
          server.connect(uri)
        } else {
          // Send them our seen uris..
          var list:List[Any] = seen.filterNot(x=> listed.contains(x) || x==server.me )
          list = list ::: List("end");
          start_write_data(list, ^{
            // once done, start sending heartbeats.
            start_write_hearbeat
          })
        }
      }
    }

    def start_read_client_listings = {
      read_source.setEventHandler(read_clientlistings)
      read_source.resume
    }

    def read_clientlistings = ^ {
      val message = read_frame
      if (message!=null) {
        if( message != "end" ) {
          server.connect(URI.create(message))
        } else {
          start_read_hearbeat
        }
      }
    }

    def start_write_hearbeat:Unit = {
      queue.dispatchAfter(^{
        trace("ping");
        start_write_data("ping"::Nil, ^{
          start_write_hearbeat
        })
      }, 1, TimeUnit.SECONDS);
    }


    def start_read_hearbeat = {
      read_source.setEventHandler(read_hearbeat)
      read_source.resume
    }

    def read_hearbeat = ^ {
      val message = read_frame
      if (message != null) {
        trace("pong");
      }
    }

    def start_write_data(list:List[Any], onDone:Runnable) = {
      val baos = new ByteArrayOutputStream()
      list.foreach { next =>
        baos.write(next.toString().getBytes("UTF-8"))
        baos.write(0)
      }
      val buffer = ByteBuffer.wrap(baos.toByteArray)
      write_source.setEventHandler(write_data(buffer, onDone))
      write_source.resume
    }

    def write_data(buffer:ByteBuffer, onDone:Runnable) = ^ {
      channel.write(buffer)
      if (buffer.remaining == 0) {
        write_source.suspend
        onDone.run
      }
    }

    def read_frame(): String = {
      if( channel.read(read_buffer) == -1 ) {
        throw new EOFException();
      }
      val buf = read_buffer.array
      val endPos = eof(buf, 0, read_buffer.position)
      if (endPos < 0) {
        trace(" --- ");
        return null
      }
      var rc = new String(buf, 0, endPos)
      val newPos = read_buffer.position - endPos
      System.arraycopy(buf, endPos + 1, buf, 0, newPos)
      read_buffer.position(newPos)
      //      trace(rc);
      return rc
    }

    def eof(data: Array[Byte], offset: Int, pos: Int): Int = {
      var i = offset
      while (i < pos) {
        if (data(i) == 0) {
          return i
        }
        i += 1
      }
      return - 1
    }

    def trace(str: String) = {
      println(String.format("%5d %5d - %s", new Integer(server.port), new Integer(uri.getPort()), str));
    }


  }
}


