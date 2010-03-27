/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fusesource.hawtdispatch.example

import _root_.java.io._
import java.net.{ProtocolException, InetSocketAddress, URI, Socket}
import buffer.AsciiBuffer
import java.util.concurrent.atomic.AtomicLong;
import java.lang.String._
import java.util.concurrent.TimeUnit._
import collection.mutable.Map

/**
 *
 * Simulates load on the a stomp broker.
 *
 */
object StompLoadClient {

  val NANOS_PER_SECOND = NANOSECONDS.convert(1, SECONDS);
  import StompLoadClient._
  implicit def toAsciiBuffer(value: String) = new AsciiBuffer(value)

  var producerSleep = 0;
  var consumerSleep = 0;
  var producers = 1;
  var consumers = 1;
  var sampleInterval = 5 * 1000;
  var uri = "stomp://127.0.0.1:61613";
  var bufferSize = 64*1204

  val producerCounter = new AtomicLong();
  val consumerCounter = new AtomicLong();

  def main(args:Array[String]) = {

    for (i <- 0 until producers) {
      val producerThread = new ProducerThread(i);
      producerThread.start();
    }

    for (i <- 0 until consumers) {
      val consumerThread = new ConsumerThread(i);
      consumerThread.start();
    }

    var start = System.nanoTime();
    while( true ) {
      Thread.sleep(sampleInterval);
      val end = System.nanoTime();
      printRate("Producer", producerCounter, end - start);
      printRate("Consumer", consumerCounter, end - start);
      start = end;
    }
  }


  def printRate(name: String, counter: AtomicLong, nanos: Long) = {
    val c = counter.getAndSet(0);
    val rate_per_second: java.lang.Float = ((1.0f * c / nanos) * NANOS_PER_SECOND);
    println(format("%s rate: %,.3f per second", name, rate_per_second));
  }


  object StompClient {
    def connect(proc: StompClient=>Unit ) = {
      val client = new StompClient();
      try {
        val connectUri = new URI(uri);
        client.open(connectUri.getHost(), connectUri.getPort());
        client.send("""CONNECT

""")
        client.flush
        client.receive("CONNECTED")
        proc(client)
      } catch {
        case e: Throwable =>
//          e.printStackTrace();
          println("failure occured: "+e);
          Thread.sleep(1000);
      } finally {
        try {
          client.close();
        } catch {
          case ignore: Throwable =>
        }
      }
    }
  }

  class StompClient {

    var socket:Socket = null
    var out:OutputStream = null;
    var in:InputStream = null

    def open(host: String, port: Int) = {
      socket = new Socket
      socket.connect(new InetSocketAddress(host, port))
      socket.setSoLinger(true, 0);
      out = new BufferedOutputStream(socket.getOutputStream, bufferSize)
      in = new BufferedInputStream(socket.getInputStream, bufferSize)
    }

    def close() = {
      if( socket!=null ) {
        socket.close
        socket = null
        out = null
        in = null
      }
    }

    def flush() = {
      out.flush
    }

    def send(frame:String) = {
      out.write(frame.getBytes("UTF-8"))
      out.write(0)
      out.write('\n')
    }

    def receive():String = {
      val buffer = new ByteArrayOutputStream(500)
      var c = in.read;
      while( c >= 0 ) {
        if( c==0 ) {
          return new String(buffer.toByteArray, "UTF-8")
        }
        buffer.write(c);
        c = in.read()
      }
      throw new EOFException()
    }

    def receive(expect:String):String = {
      val rc = receive()
      if( !rc.trimFront.startsWith(expect) ) {
        throw new ProtocolException("Expected "+expect)
      }
      rc
    }

  }

  class ProducerThread(val id: Int) extends Thread {
    val name: String = "producer " + id;

    override def run() {
      while (true) {
        StompClient.connect { client =>
          var i =0;
          while (true) {
            client.send("""
SEND
destination:/queue/test"""+id+"""

Message #""" + i + " from " + name)
            producerCounter.incrementAndGet();
            Thread.sleep(producerSleep);
            i += 1
          }
        }
      }
    }
  }

  class ConsumerThread(val id: Int) extends Thread {
    val name: String = "producer " + id;

    override def run() {
      while (true) {
        StompClient.connect { client =>
          val headers = Map[AsciiBuffer, AsciiBuffer]();
          client.send("""
SUBSCRIBE
destination:/queue/test"""+id+"""

""")
          client.flush

          while (true) {
            client.receive("MESSAGE");
            consumerCounter.incrementAndGet();
            Thread.sleep(consumerSleep);
          }
        }
      }
    }
  }

}
