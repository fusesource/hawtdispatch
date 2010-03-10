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
package org.fusesource.hawtdispatch.example.stomp;

import org.fusesource.hawtdispatch.example.stomp.buffer.AsciiBuffer;
import org.fusesource.hawtdispatch.example.stomp.buffer.Buffer;

public interface Stomp {
    
    Buffer EMPTY_BUFFER = new Buffer(0);
    byte NULL = 0;
    Buffer NULL_BUFFER = new Buffer(new byte[]{NULL});
    byte NEWLINE = '\n';
    Buffer NEWLINE_BUFFER = new Buffer(new byte[]{NEWLINE});
    Buffer END_OF_FRAME_BUFFER = new Buffer(new byte[]{NULL, NEWLINE});
    
    AsciiBuffer TRUE = new AsciiBuffer("true");
    AsciiBuffer FALSE = new AsciiBuffer("false");

    public static interface Commands {
        AsciiBuffer CONNECT = new AsciiBuffer("CONNECT");
        AsciiBuffer SEND = new AsciiBuffer("SEND");
        AsciiBuffer DISCONNECT = new AsciiBuffer("DISCONNECT");
        AsciiBuffer SUBSCRIBE = new AsciiBuffer("SUB");
        AsciiBuffer UNSUBSCRIBE = new AsciiBuffer("UNSUB");

        AsciiBuffer BEGIN_TRANSACTION = new AsciiBuffer("BEGIN");
        AsciiBuffer COMMIT_TRANSACTION = new AsciiBuffer("COMMIT");
        AsciiBuffer ABORT_TRANSACTION = new AsciiBuffer("ABORT");
        AsciiBuffer BEGIN = new AsciiBuffer("BEGIN");
        AsciiBuffer COMMIT = new AsciiBuffer("COMMIT");
        AsciiBuffer ABORT = new AsciiBuffer("ABORT");
        AsciiBuffer ACK = new AsciiBuffer("ACK");
    }

    public interface Responses {
        AsciiBuffer CONNECTED = new AsciiBuffer("CONNECTED");
        AsciiBuffer ERROR = new AsciiBuffer("ERROR");
        AsciiBuffer MESSAGE = new AsciiBuffer("MESSAGE");
        AsciiBuffer RECEIPT = new AsciiBuffer("RECEIPT");
    }

    public interface Headers {
        byte SEPERATOR = ':';
        Buffer SEPERATOR_BUFFER = new Buffer(new byte[]{SEPERATOR});
        
        AsciiBuffer RECEIPT_REQUESTED = new AsciiBuffer("receipt");
        AsciiBuffer TRANSACTION = new AsciiBuffer("transaction");
        AsciiBuffer CONTENT_LENGTH = new AsciiBuffer("content-length");
        AsciiBuffer TRANSFORMATION = new AsciiBuffer("transformation");
        AsciiBuffer TRANSFORMATION_ERROR = new AsciiBuffer("transformation-error");

        public interface Response {
            AsciiBuffer RECEIPT_ID = new AsciiBuffer("receipt-id");
        }

        public interface Send {
            AsciiBuffer DESTINATION = new AsciiBuffer("destination");
            AsciiBuffer CORRELATION_ID = new AsciiBuffer("correlation-id");
            AsciiBuffer REPLY_TO = new AsciiBuffer("reply-to");
            AsciiBuffer EXPIRATION_TIME = new AsciiBuffer("expires");
            AsciiBuffer PRIORITY = new AsciiBuffer("priority");
            AsciiBuffer TYPE = new AsciiBuffer("type");
            AsciiBuffer PERSISTENT = new AsciiBuffer("persistent");
        }

        public interface Message {
            AsciiBuffer MESSAGE_ID = new AsciiBuffer("message-id");
            AsciiBuffer DESTINATION = new AsciiBuffer("destination");
            AsciiBuffer CORRELATION_ID = new AsciiBuffer("correlation-id");
            AsciiBuffer EXPIRATION_TIME = new AsciiBuffer("expires");
            AsciiBuffer REPLY_TO = new AsciiBuffer("reply-to");
            AsciiBuffer PRORITY = new AsciiBuffer("priority");
            AsciiBuffer REDELIVERED = new AsciiBuffer("redelivered");
            AsciiBuffer TIMESTAMP = new AsciiBuffer("timestamp");
            AsciiBuffer TYPE = new AsciiBuffer("type");
            AsciiBuffer SUBSCRIPTION = new AsciiBuffer("subscription");
        }

        public interface Subscribe {
            AsciiBuffer DESTINATION = new AsciiBuffer("destination");
            AsciiBuffer ACK_MODE = new AsciiBuffer("ack");
            AsciiBuffer ID = new AsciiBuffer("id");
            AsciiBuffer SELECTOR = new AsciiBuffer("selector");

            public interface AckModeValues {
                AsciiBuffer AUTO = new AsciiBuffer("auto");
                AsciiBuffer CLIENT = new AsciiBuffer("client");
                AsciiBuffer INDIVIDUAL = new AsciiBuffer("client-individual");
            }
        }

        public interface Unsubscribe {
            AsciiBuffer DESTINATION = new AsciiBuffer("destination");
            AsciiBuffer ID = new AsciiBuffer("id");
        }

        public interface Connect {
            AsciiBuffer LOGIN = new AsciiBuffer("login");
            AsciiBuffer PASSCODE = new AsciiBuffer("passcode");
            AsciiBuffer CLIENT_ID = new AsciiBuffer("client-id");
            AsciiBuffer REQUEST_ID = new AsciiBuffer("request-id");
        }

        public interface Error {
            AsciiBuffer MESSAGE = new AsciiBuffer("message");
        }

        public interface Connected {
            AsciiBuffer SESSION = new AsciiBuffer("session");
            AsciiBuffer RESPONSE_ID = new AsciiBuffer("response-id");
        }

        public interface Ack {
            AsciiBuffer MESSAGE_ID = new AsciiBuffer("message-id");
        }
    }
    
	public enum Transformations {
		JMS_BYTE, JMS_OBJECT_XML, JMS_OBJECT_JSON, JMS_MAP_XML, JMS_MAP_JSON;
		
		public String toString() {
			return name().replaceAll("_", "-").toLowerCase();
		}
		
		public static Transformations getValue(String value) {
			return valueOf(value.replaceAll("-", "_").toUpperCase());
		}
	}    
}
