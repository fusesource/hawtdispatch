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

import java.nio.channels.{SocketChannel}
import java.nio.ByteBuffer
import org.fusesource.hawtdispatch.example.Stomp._
import org.fusesource.hawtdispatch.example.Stomp.Headers._
import java.util.{ArrayList}

import collection.mutable.HashMap
import java.io.{EOFException, IOException}
import org.fusesource.hawtdispatch.example.buffer._
import AsciiBuffer._

object StompWireFormat {
    val MAX_COMMAND_LENGTH = 1024;
    val MAX_HEADER_LENGTH = 1024 * 10;
    val MAX_HEADERS = 1000;
    val MAX_DATA_LENGTH = 1024 * 1024 * 100;
    val TRIM=true
  }

class StompWireFormat {
  import StompWireFormat._

  implicit def ByteBufferWrapper(x: Buffer) = ByteBuffer.wrap(x.data, x.offset, x.length);
  implicit def ByteWrapper(x: Byte) = {
    ByteBuffer.wrap(Array(x));
  }

  var outbound_frame: Array[ByteBuffer] = null

  /**
   * @retruns true if the source has been drained of StompFrame objects they are fully written to the socket
   */
  def drain_to_socket(socket:SocketChannel)(source: =>StompFrame ):Boolean = {
    while(true) {
      // if we have a pending frame that is being sent over the socket...
      if( outbound_frame!=null ) {
        socket.write(outbound_frame)
        if( outbound_frame.last.remaining != 0 ) {
          // non blocking socket returned before the buffers were fully written to disk..
          // we are not yet fully drained.. but need to quit now.
          return false
        } else {
          outbound_frame = null
        }
      } else {
        val frame = source
        if( frame==null ) {
          // the source is now drained...
          return true
        } else {
          outbound_frame = marshall( frame )
        }
      }
    }
    true
  }

  def marshall(stomp:StompFrame) = {
      val list = new ArrayList[ByteBuffer]()
      list.add(stomp.action)
      list.add(NEWLINE)

      for ((key, value) <- stomp.headers.elements ) {
          list.add(key)
          list.add(SEPERATOR)
          list.add(value)
          list.add(NEWLINE)
      }
      list.add(NEWLINE)
      list.add(stomp.content)
      list.add(END_OF_FRAME_BUFFER)
      list.toArray(Array.ofDim[ByteBuffer](list.size))
  }


  var socket_buffer:ByteBuffer = null

  def read_socket(socket:SocketChannel)(handler:(StompFrame)=>Boolean) = {
    def fill_buffer() = {
      if( socket.read(socket_buffer) == -1 ) {
        throw new EOFException();
      }
      socket_buffer.flip
      socket_buffer.remaining==0
    }

    var done = false
    if( socket_buffer==null ) {
      socket_buffer = ByteBuffer.allocate(8 * 1024);
      done = fill_buffer();
    }

    // keep going until the socket buffer is drained.
    while( !done ) {
      done = unmarshall(socket_buffer) match {
        // The handler can return true to stop reading the buffer...
        case Some(frame)=>
          handler(frame)
        case None=>
          if( socket_buffer.remaining==0 ) {
            socket_buffer.clear
            // we can only continue reading if there is data on the socket
            fill_buffer()
          } else {
            // there is data left.. keep un-marshalling it.
            false
          }
      }
    }

    // Only release the memory if we have fully consumed the buffer data..
    // it is possible the handler wants to stop reading data before the buffer is
    // fully consumed.
    if( socket_buffer.remaining==0 ) {
      socket_buffer = null
    }
  }


  type FrameReader = (ByteBuffer)=>Option[StompFrame]
  var unmarshall:FrameReader = read_action

  var line_buffer:ByteArrayOutputStream = null

  def read_line(in:ByteBuffer, maxLength:Int, errorMessage:String):Option[Buffer] = {
      if( line_buffer == null ) {
        line_buffer = new ByteArrayOutputStream(40);
      }
      var b = 0;
      while (in.remaining()>0) {
          b = in.get();
          if( b=='\n') {
              var rc = line_buffer.toBuffer();
              line_buffer = null
              return Some(rc);
          }
          if (line_buffer.size() > maxLength) {
              throw new IOException(errorMessage);
          }
          line_buffer.write(b);
      }
      return None;
  }


  def read_action:FrameReader = (in:ByteBuffer)=> {
    read_line(in, MAX_COMMAND_LENGTH, "The maximum command length was exceeded") match {
      case Some(line)=>
        var action = line
        if( TRIM ) {
            action = action.trim();
        }
        if (action.length() > 0) {
            unmarshall = read_headers(action)
        }
      case None=>
    }
    None
  }

  type HeaderMap = collection.mutable.Map[AsciiBuffer, AsciiBuffer]

  def read_headers(action:Buffer, headers:HeaderMap=new HashMap[AsciiBuffer,AsciiBuffer]()):FrameReader = (in:ByteBuffer)=> {
    read_line(in, MAX_COMMAND_LENGTH, "The maximum command length was exceeded") match {
      case Some(line)=>
        if( line.trim().length() > 0 ) {

          if (headers.size > MAX_HEADERS) {
              throw new IOException("The maximum number of headers was exceeded");
          }

          try {
              val seperatorIndex = line.indexOf(SEPERATOR);
              if( seperatorIndex<0 ) {
                  throw new IOException("Header line missing seperator [" + ascii(line) + "]");
              }
              var name = line.slice(0, seperatorIndex);
              if( TRIM ) {
                  name = name.trim();
              }
              var value = line.slice(seperatorIndex + 1, line.length());
              if( TRIM ) {
                  value = value.trim();
              }
              headers.put(ascii(name), ascii(value));
          } catch {
              case e:Exception=>
                throw new IOException("Unable to parser header line [" + line + "]");
          }

        } else {
          val contentLength = headers.get(CONTENT_LENGTH);
          if (contentLength.isDefined) {
            // Bless the client, he's telling us how much data to read in.
            var length=0;
            try {
                length = Integer.parseInt(contentLength.get.trim().toString());
            } catch {
              case e:NumberFormatException=>
                throw new IOException("Specified content-length is not a valid integer");
            }

            if (length > MAX_DATA_LENGTH) {
                throw new IOException("The maximum data length was exceeded");
            }
            unmarshall = read_body(action, headers, new Buffer(length))

          } else {
            unmarshall = read_body(action, headers, new ByteArrayOutputStream(512))
          }
        }
      case None=>
    }
    None
  }

  def read_body(action:Buffer, headers:HeaderMap, content:Buffer):FrameReader = (in:ByteBuffer)=> {
    val length = Math.min(content.length-content.offset, in.remaining)
    in.get(content.data, content.offset, length);
    content.offset += length;
    if( content.offset == content.length ) {
      content.offset = 0
      unmarshall = read_action
      Some(new StompFrame(ascii(action), headers, content))
    } else {
      None
    }
  }


  def read_body(action:Buffer, headers:HeaderMap, baos:ByteArrayOutputStream):FrameReader = (in:ByteBuffer)=> {
    var rc:Buffer=null
    var b:Byte = 0
    while( rc == null && in.remaining()>0) {
        b = in.get();
        if( b==0 ) {
            rc = baos.toBuffer();
        } else if (baos.size() > MAX_DATA_LENGTH) {
          throw new IOException("The maximum data length was exceeded");
        } else {
          baos.write(b);
        }
    }
    if( rc == null ) {
      None
    } else {
      unmarshall = read_action
      Some(new StompFrame(ascii(action), headers, rc))
    }

  }

}
