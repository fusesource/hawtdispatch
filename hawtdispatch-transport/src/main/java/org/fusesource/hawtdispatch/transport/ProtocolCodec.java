/**
 * Copyright (C) 2012 FuseSource, Inc.
 * http://fusesource.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.fusesource.hawtdispatch.transport;

import java.io.IOException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;


/**
 * Interface to encode and decode commands in and out of a a non blocking channel.
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public interface ProtocolCodec {

    public void setTransport(Transport transport);

    ///////////////////////////////////////////////////////////////////
    //
    // Methods related with reading from the channel
    //
    ///////////////////////////////////////////////////////////////////


    /**
     * Non-blocking channel based decoding.
     * 
     * @return
     * @throws IOException
     */
    Object read() throws IOException;

    /**
     * Pushes back a buffer as being unread.
     *
     * @param buffer
     */
    void unread(byte[] buffer);

    /**
     * @return The number of bytes received.
     */
    public long getReadCounter();

    /**
     * @return The number of bytes read in the last read io performed.
     */
    public long getLastReadSize();


    ///////////////////////////////////////////////////////////////////
    //
    // Methods related with writing to the channel
    //
    ///////////////////////////////////////////////////////////////////


    enum BufferState {
        EMPTY,
        WAS_EMPTY,
        NOT_EMPTY,
        FULL,
    }

    public int getReadBufferSize();
    public int getWriteBufferSize();

    /**
     * Non-blocking channel based encoding.
     *
     * @return true if the write completed.
     * @throws IOException
     */
    BufferState write(Object value) throws IOException;

    /**
     * Attempts to complete the previous write which did not complete.
     * @return
     * @throws IOException
     */
    BufferState flush() throws IOException;

    /**
     * Is the codec's buffer full?
     * @return
     */
    boolean full();

    /**
     * @return The number of bytes written.
     */
    public long getWriteCounter();

    /**
     * @return The number of bytes read in the last write io performed.
     */
    public long getLastWriteSize();

}
