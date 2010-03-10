package org.fusesource.hawtdispatch.example.stomp

import buffer.{Buffer, AsciiBuffer}
import collection.mutable.Map
import collection.mutable.HashMap

object StompFrame{
  var NO_DATA = new Buffer(0);
}

/**
 * Represents all the data in a STOMP frame.
 *
 * @author <a href="http://hiramchirino.com">chirino</a>
 */
case class StompFrame(action:AsciiBuffer, headers:Map[AsciiBuffer, AsciiBuffer]=new HashMap(), content:Buffer=StompFrame.NO_DATA) {

//    public StompFrame(AsciiBuffer command) {
//    	this(command, null, null);
//    }
//
//    public StompFrame(AsciiBuffer command, Map<AsciiBuffer, AsciiBuffer> headers) {
//    	this(command, headers, null);
//    }
//
//    public StompFrame(AsciiBuffer command, Map<AsciiBuffer, AsciiBuffer> headers, Buffer data) {
//        this.action = command;
//        if (headers != null)
//        	this.headers = headers;
//        if (data != null)
//        	this.content = data;
//    }
//
//    public StompFrame() {
//    }


//    public String toString() {
//        StringBuffer buffer = new StringBuffer();
//        buffer.append(getAction());
//        buffer.append("\n");
//
//        for (Entry<AsciiBuffer, AsciiBuffer> entry : headers.entrySet()) {
//            buffer.append(entry.getKey());
//            buffer.append(":");
//            buffer.append(entry.getValue());
//            buffer.append("\n");
//        }
//
//        buffer.append("\n");
//        if (getContent() != null) {
//            try {
//                buffer.append(getContent());
//            } catch (Throwable e) {
//            }
//        }
//        return buffer.toString();
//    }

}
