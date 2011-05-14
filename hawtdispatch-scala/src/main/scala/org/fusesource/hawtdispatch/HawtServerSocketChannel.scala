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
package org.fusesource.hawtdispatch

import util.continuations._
import java.nio.channels.{SocketChannel, SelectionKey, ServerSocketChannel}
import collection.mutable.ListBuffer
import java.nio.ByteBuffer
import java.net.SocketAddress
import java.io.IOException

/**
 * <p>
 * </p>
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
case class HawtServerSocketChannel(channel: ServerSocketChannel, queue: DispatchQueue = createQueue()) {

  channel.configureBlocking(false);

  var accept_requests = ListBuffer[(Either[SocketChannel, IOException]) => Unit]()
  val accept_source = createSource(channel, SelectionKey.OP_ACCEPT, queue)
  accept_source.onEvent {

    var source_drained = false
    while (!source_drained && !accept_requests.isEmpty) {
      val k = accept_requests.head
      try {
        val socket = channel.accept
        if (socket == null) {
          source_drained = true
        } else {
          accept_requests = accept_requests.drop(1)
          k(Left(socket))
        }
      } catch {
        case e: IOException =>
          k(Right(e))
      }
    }

    if (accept_requests.isEmpty) {
      accept_source.suspend
    }
  }


  def accept: Either[SocketChannel, IOException]@suspendable = shift { k:(Either[SocketChannel, IOException]=>Unit) =>
    queue {
      if( canceled ) {
        k(Right(new IOException("canceled")))
      } else {
        if (accept_requests.isEmpty) {
          accept_source.resume
        }
        accept_requests.append(k)
      }
    }
  }

  var canceled = false
  var cancel_requests = ListBuffer[ Unit => Unit]()
  accept_source.onCancel {
    canceled = true
    accept_requests.foreach(_(Right(new IOException("canceled"))))
    accept_requests.clear
    cancel_requests.foreach(_({}))
    cancel_requests.clear
  }

  def cancel: Unit @suspendable = shift { k:(Unit=>Unit) =>
    queue {
      if( canceled ) {
        k({})
      } else {
        cancel_requests.append(k)
        accept_source.cancel
      }
    }
  }

}


/**
 * <p>
 * </p>
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
case class HawtSocketChannel(channel: SocketChannel, queue: DispatchQueue = createQueue()) {

  channel.configureBlocking(false);

  var connect_request: (Option[IOException])=>Unit = _
  var connect_source:DispatchSource = _

  def connect(address:SocketAddress): Option[IOException]@suspendable = shift { k:(Option[IOException]=>Unit) =>
    queue {
      if( canceled ) {
        k(Some(new IOException("canceled")))
      } else {
        try {
          if( channel.connect(address) ) {
            k(None)
          } else {
            connect_request = k
            connect_source = createSource(channel, SelectionKey.OP_CONNECT, queue)

            def check = if( !connect_source.isCanceled ) {
              if( connect_source!=null ) {
                try {
                  if(channel.finishConnect) {
                    connect_source.cancel
                    connect_source = null
                    connect_request = null
                    k(None)
                  }
                } catch {
                  case e:IOException =>
                    connect_source.cancel
                    connect_source = null
                    connect_request = null
                    k(Some(e))
                }
              }
            }

            connect_source.onEvent {
              check
            }
            connect_source.resume

// TODO: perhaps we should introduce a connect timeout...
//            def schedual_check:Unit = queue.after(5,TimeUnit.SECONDS) {
//              check
//              if( connect_source==null ) {
//                schedual_check
//              }
//            }
//            schedual_check

          }

        } catch {
          case e:IOException => k(Some(e))
        }
      }
    }
  }


  var read_requests = ListBuffer[(ByteBuffer, (Option[IOException]) => Unit)]()
  val read_source = createSource(channel, SelectionKey.OP_READ, queue)
  read_source.onEvent {
    var source_drained = false
    while (!source_drained && !read_requests.isEmpty) {
      val k = read_requests.head
      try {
        val remaining = k._1.remaining
        val count = channel.read(k._1)
        if (count == 0 && remaining > 0 ) {
          source_drained = true
        } else {
          read_requests = read_requests.drop(1)
          k._2(None)
        }
      } catch {
        case e: IOException =>
          k._2(Some(e))
      }
    }

    if (read_requests.isEmpty) {
      read_source.suspend
    }
  }

  def read(buffer:ByteBuffer): Option[IOException]@suspendable = shift { k:(Option[IOException]=>Unit) =>
    queue {
      if( canceled ) {
        k(canceled_exception)
      } else {
        if (read_requests.isEmpty) {
          read_source.resume
        }
        read_requests.append((buffer,k))
      }
    }
  }

  var write_requests = ListBuffer[(ByteBuffer, (Option[IOException]) => Unit)]()
  val write_source = createSource(channel, SelectionKey.OP_WRITE, queue)
  write_source.onEvent {
    var source_drained = false
    while (!source_drained && !write_requests.isEmpty) {
      val k = write_requests.head
      try {
        val remaining = k._1.remaining
        val count = channel.write(k._1)
        if (count == 0 && remaining > 0 ) {
          source_drained = true
        } else {
          if( k._1.remaining==0 ) {
            write_requests = write_requests.drop(1)
            k._2(None)
          }
        }
      } catch {
        case e: IOException =>
          k._2(Some(e))
      }
    }

    if (write_requests.isEmpty) {
      write_source.suspend
    }
  }

  def write(buffer:ByteBuffer): Option[IOException]@suspendable = shift { k:(Option[IOException]=>Unit) =>
    queue {
      if( canceled ) {
        k(canceled_exception)
      } else {
        if (write_requests.isEmpty) {
          write_source.resume
        }
        write_requests.append((buffer,k))
      }
    }
  }

  var canceled = false
  var cancel_requests = ListBuffer[ Unit => Unit]()
  read_source.onCancel {
    write_source.cancel
  }

  write_source.onCancel {
    canceled = true
    if( connect_source!=null ) {
      connect_request(canceled_exception)
      connect_request = null
      connect_source.cancel
      connect_source = null
    }
    read_requests.foreach(_._2(canceled_exception))
    read_requests.clear
    write_requests.foreach(_._2(canceled_exception))
    write_requests.clear
    cancel_requests.foreach(_({}))
    cancel_requests.clear
  }

  def canceled_exception=Some(new IOException("canceled"))

  def cancel:Unit @suspendable = shift { k:(Unit=>Unit) =>
    queue {
      if( canceled ) {
        k({})
      } else {
        cancel_requests.append(k)
        read_source.cancel
      }
    }
  }

}