/**
 * Copyright (C) 2010-2011, FuseSource Corp.  All rights reserved.
 *
 *     http://fusesource.com
 *
 * The software in this package is published under the terms of the
 * CDDL license a copy of which has been included with this distribution
 * in the license.txt file.
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

    ///////////////////////////////////////////////////////////////////
    //
    // Methods related with reading from the channel
    //
    ///////////////////////////////////////////////////////////////////

    /**
     * @param channel
     */
    public void setReadableByteChannel(ReadableByteChannel channel) throws Exception;

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

    public void setWritableByteChannel(WritableByteChannel channel) throws Exception;

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
