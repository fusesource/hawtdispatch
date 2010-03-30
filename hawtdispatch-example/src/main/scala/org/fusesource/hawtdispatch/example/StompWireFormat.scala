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

import _root_.java.util.{LinkedList, ArrayList}
import java.nio.channels.{SocketChannel}
import java.nio.ByteBuffer
import org.fusesource.hawtdispatch.example.Stomp._
import org.fusesource.hawtdispatch.example.Stomp.Headers._
import java.io.{EOFException, IOException}
import org.fusesource.hawtdispatch.example.buffer._
import AsciiBuffer._
import collection.mutable.{ListBuffer, HashMap}

object StompWireFormat {
    val READ_BUFFFER_SIZE = 1024*1024;
    val MAX_COMMAND_LENGTH = 1024;
    val MAX_HEADER_LENGTH = 1024 * 10;
    val MAX_HEADERS = 1000;
    val MAX_DATA_LENGTH = 1024 * 1024 * 100;
    val TRIM=false
    val SIZE_CHECK=false
  }

class StompWireFormat {
  import StompWireFormat._

  implicit def ByteBufferWrapper(x: Buffer) = ByteBuffer.wrap(x.data, x.offset, x.length);
  implicit def ByteWrapper(x: Byte) = {
    ByteBuffer.wrap(Array(x));
  }

//  var outbound_pos=0
//  var outbound_limit=0
//  var outbound_buffers: ListBuffer[ByteBuffer] = new ListBuffer[ByteBuffer]()
//
//  /**
//   * @retruns true if the source has been drained of StompFrame objects and they are fully written to the socket
//   */
//  def drain_source(socket:SocketChannel)(source: =>StompFrame ):Boolean = {
//    while(true) {
//      // if we have a pending frame that is being sent over the socket...
//      if( !outbound_buffers.isEmpty ) {
//
//        val data = outbound_buffers.toArray
//
//        socket.write(data)
//
//        // remove all the written buffers...
//        while( !outbound_buffers.isEmpty && outbound_buffers.head.remaining==0 ) {
//          outbound_buffers.remove(0)
//        }
//
//        if( !outbound_buffers.isEmpty ) {
//          // non blocking socket returned before the buffers were fully written to disk..
//          // we are not yet fully drained.. but need to quit now.
//          return false
//        }
//
//      } else {
//
//        var frame = source
//        while( frame!=null ) {
//          marshall(outbound_buffers, frame)
//          frame = source
//        }
//
//        if( outbound_buffers.size == 0 ) {
//          // the source is now drained...
//          return true
//        }
//      }
//    }
//    true
//  }
//
//  implicit def toByteBuffer(data:AsciiBuffer) = ByteBuffer.wrap(data.data, data.offset, data.length)
//  implicit def toByteBuffer(data:Buffer) = ByteBuffer.wrap(data.data, data.offset, data.length)
//
//  def marshall(buffer:ListBuffer[ByteBuffer], frame:StompFrame) = {
//    buffer.append(frame.action)
//    buffer.append(NEWLINE)
//
//    // we can optimize a little if the headers and content are in the same buffer..
//    if( !frame.headers.isEmpty && !frame.content.isEmpty &&
//            ( frame.headers.getFirst._1.data eq frame.content.data ) ) {
//      buffer.append(  ByteBuffer.wrap(frame.content.data, frame.headers.getFirst._1.offset, (frame.content.offset-frame.headers.getFirst._1.offset)+ frame.content.length) )
//
//    } else {
//      val i = frame.headers.iterator
//      while( i.hasNext ) {
//        val (key, value) = i.next
//        buffer.append(key)
//        buffer.append(SEPERATOR)
//        buffer.append(value)
//        buffer.append(NEWLINE)
//      }
//
//      buffer.append(NEWLINE)
//      buffer.append(toByteBuffer(frame.content))
//    }
//    buffer.append(toByteBuffer(END_OF_FRAME_BUFFER))
//  }

  var outbound_frame: ByteBuffer = null
  /**
   * @retruns true if the source has been drained of StompFrame objects and they are fully written to the socket
   */
  def drain_source(socket:SocketChannel)(source: =>StompFrame ):Boolean = {
    while(true) {
      // if we have a pending frame that is being sent over the socket...
      if( outbound_frame!=null ) {
        socket.write(outbound_frame)
        if( outbound_frame.remaining != 0 ) {
          // non blocking socket returned before the buffers were fully written to disk..
          // we are not yet fully drained.. but need to quit now.
          return false
        } else {
          outbound_frame = null
        }
      } else {

        // marshall all the available frames..
        val buffer = new ByteArrayOutputStream()
        var frame = source
        while( frame!=null ) {
          marshall(buffer, frame)
          frame = source
        }


        if( buffer.size() ==0 ) {
          // the source is now drained...
          return true
        } else {
          val b = buffer.toBuffer;
          outbound_frame = ByteBuffer.wrap(b.data, b.offset, b.length)
        }
      }
    }
    true
  }

  def marshall(buffer:ByteArrayOutputStream, frame:StompFrame) = {
    buffer.write(frame.action)
    buffer.write(NEWLINE)

    // we can optimize a little if the headers and content are in the same buffer..
    if( !frame.headers.isEmpty && !frame.content.isEmpty &&
            ( frame.headers.getFirst._1.data eq frame.content.data ) ) {
      buffer.write( frame.content.data, frame.headers.getFirst._1.offset, (frame.content.offset-frame.headers.getFirst._1.offset)+ frame.content.length )

    } else {
      val i = frame.headers.iterator
      while( i.hasNext ) {
        val (key, value) = i.next
        buffer.write(key)
        buffer.write(SEPERATOR)
        buffer.write(value)
        buffer.write(NEWLINE)
      }

      buffer.write(NEWLINE)
      buffer.write(frame.content)
    }
    buffer.write(END_OF_FRAME_BUFFER)
  }


  var read_pos = 0
  var read_offset = 0
  var read_data:Array[Byte] = new Array[Byte](READ_BUFFFER_SIZE)
  var read_bytebuffer:ByteBuffer = ByteBuffer.wrap(read_data)

  def drain_socket(socket:SocketChannel)(handler:(StompFrame)=>Boolean) = {
    var done = false

    // keep going until the socket buffer is drained.
    while( !done ) {
      val frame = unmarshall()
      if( frame!=null ) {
        // the handler might want us to stop looping..
        done = handler(frame)
      } else {

        // do we need to read in more data???
        if( read_pos==read_bytebuffer.position ) {

          // do we need a new data buffer to read data into??
          if(read_bytebuffer.remaining==0) {

            // The capacity needed grows by powers of 2...
            val new_capacity = if( read_offset != 0 ) { READ_BUFFFER_SIZE } else { read_data.length << 2 }
            val tmp_buffer = new Array[Byte](new_capacity)

            // If there was un-consummed data.. copy it over...
            val size = read_pos - read_offset
            if( size > 0 ) {
              System.arraycopy(read_data, read_offset, tmp_buffer, 0, size)
            }
            read_data = tmp_buffer
            read_bytebuffer = ByteBuffer.wrap(read_data)
            read_bytebuffer.position(size)
            read_offset = 0;
            read_pos = size

          }

          // Try to fill the buffer with data from the nio socket..
          var p = read_bytebuffer.position
          if( socket.read(read_bytebuffer) == -1 ) {
            throw new EOFException();
          }
          // we are done if there was no data on the socket.
          done = read_bytebuffer.position==p
        }
      }
    }
  }


  type FrameReader = ()=>StompFrame
  var unmarshall:FrameReader = read_action

  def read_line( maxLength:Int, errorMessage:String):Buffer = {
      val read_limit = read_bytebuffer.position
      while( read_pos < read_limit ) {
        if( read_data(read_pos) =='\n') {
          var rc = new Buffer(read_data, read_offset, read_pos-read_offset)
          read_pos += 1;
          read_offset = read_pos;
          return rc
        }
        if (SIZE_CHECK && read_pos-read_offset > maxLength) {
            throw new IOException(errorMessage);
        }
        read_pos += 1;
      }
      return null;
  }


  def read_action:FrameReader = ()=> {
    val line = read_line(MAX_COMMAND_LENGTH, "The maximum command length was exceeded")
    if( line !=null ) {
      var action = line
      if( TRIM ) {
          action = action.trim();
      }
      if (action.length() > 0) {
          unmarshall = read_headers(action)
      }
    }
    null
  }

  type HeaderMap = LinkedList[(AsciiBuffer, AsciiBuffer)]

  def read_headers(action:Buffer, headers:HeaderMap=new LinkedList()):FrameReader = ()=> {
    val line = read_line(MAX_HEADER_LENGTH, "The maximum header length was exceeded")
    if( line !=null ) {
      if( line.trim().length() > 0 ) {

        if (SIZE_CHECK && headers.size > MAX_HEADERS) {
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
            headers.add((ascii(name), ascii(value)));
        } catch {
            case e:Exception=>
              throw new IOException("Unable to parser header line [" + line + "]");
        }

      } else {
        val contentLength = get(headers, CONTENT_LENGTH)
        if (contentLength.isDefined) {
          // Bless the client, he's telling us how much data to read in.
          var length=0;
          try {
              length = Integer.parseInt(contentLength.get.trim().toString());
          } catch {
            case e:NumberFormatException=>
              throw new IOException("Specified content-length is not a valid integer");
          }

          if (SIZE_CHECK && length > MAX_DATA_LENGTH) {
              throw new IOException("The maximum data length was exceeded");
          }
          unmarshall = read_binary_body(action, headers, length)

        } else {
          unmarshall = read_text_body(action, headers)
        }
      }
    }
    null
  }

  def get(headers:HeaderMap, name:AsciiBuffer):Option[AsciiBuffer] = {
    val i = headers.iterator
    while( i.hasNext ) {
      val entry = i.next
      if( entry._1 == name ) {
        return Some(entry._2)
      }
    }
    None
  }
  

  def read_binary_body(action:Buffer, headers:HeaderMap, contentLength:Int):FrameReader = ()=> {
    val content:Buffer=read_content(contentLength)
    if( content != null ) {
      unmarshall = read_action
      new StompFrame(ascii(action), headers, content)
    } else {
      null
    }
  }


  def read_content(contentLength:Int):Buffer = {
      val read_limit = read_bytebuffer.position
      if( (read_limit-read_offset)+1 < contentLength ) {
        read_pos = read_limit;
        null
      } else {
        if( read_data(read_offset+contentLength)!= 0 ) {
           throw new IOException("Exected null termintor after "+contentLength+" content bytes");
        }
        var rc = new Buffer(read_data, read_offset, contentLength)
        read_pos = read_offset+contentLength+1;
        read_offset = read_pos;
        rc;
      }
  }

  def read_to_null():Buffer = {
      val read_limit = read_bytebuffer.position
      while( read_pos < read_limit ) {
        if( read_data(read_pos) ==0) {
          var rc = new Buffer(read_data, read_offset, read_pos-read_offset)
          read_pos += 1;
          read_offset = read_pos;
          return rc;
        }
        read_pos += 1;
      }
      return null;
  }


  def read_text_body(action:Buffer, headers:HeaderMap):FrameReader = ()=> {
    val content:Buffer=read_to_null
    if( content != null ) {
      unmarshall = read_action
      new StompFrame(ascii(action), headers, content)
    } else {
      null
    }
  }



}
