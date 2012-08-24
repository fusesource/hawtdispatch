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
import java.net.SocketAddress;
import java.net.URI;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.Executor;

import org.fusesource.hawtdispatch.DispatchQueue;
import org.fusesource.hawtdispatch.Task;

/**
 * Represents an abstract connection.  It can be a client side or server side connection.
 * 
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public interface Transport {

    /**
     * Starts the service.  Executes the onComplete runnable once the service has fully started up.
     *
     * @param onComplete my be set to null if not interested in a callback.
     */
    void start(Runnable onComplete);

    /**
     * Stops the service.  Executes the onComplete runnable once the service has fully stopped.
     *
     * @param onComplete my be set to null if not interested in a callback.
     */
    void stop(Runnable onComplete);

    /**
     * Starts the service.  Executes the onComplete runnable once the service has fully started up.
     *
     * @param onComplete my be set to null if not interested in a callback.
     */
    void start(Task onComplete);

    /**
     * Stops the service.  Executes the onComplete runnable once the service has fully stopped.
     *
     * @param onComplete my be set to null if not interested in a callback.
     */
    void stop(Task onComplete);

    boolean full();

    /**
     * A one way asynchronous send of a command.  Only sent if the the transport is not full.
     * 
     * @param command
     * @return true if the command was accepted.
     */
    boolean offer(Object command);

    /**
     * Forces a flush of any output buffers.  Once the flush completes the listener's
     * 'onRefill()' method will execute.
     */
    public void flush();

    /**
     * Returns the current transport listener
     *
     * @return
     */
    TransportListener getTransportListener();

    /**
     * Registers an inbound command listener
     *
     * @param transportListener
     */
    void setTransportListener(TransportListener transportListener);

    /**
     * Returns the dispatch queue used by the transport
     *
     * @return
     */
    DispatchQueue getDispatchQueue();

    /**
     * Sets the dispatch queue used by the transport
     *
     * @param queue
     */
    void setDispatchQueue(DispatchQueue queue);

    /**
     * suspend delivery of commands.
     */
    void suspendRead();

    /**
     * resume delivery of commands.
     */
    void resumeRead();

    /**
     * @return the remote address for this connection
     */
    SocketAddress getRemoteAddress();

    /**
     * @return the remote address for this connection
     */
    SocketAddress getLocalAddress();

    public void drainInbound();

    /**
     * @return true if the transport is closed/stopped.
     */
    boolean isClosed();
    
    /**
     * @return true if the transport is connected
     */
    boolean isConnected();
    
    /**
     * @return The protocol codec for the transport.
     */
    ProtocolCodec getProtocolCodec();

    /**
     * Sets the protocol codec for the transport
     * @param protocolCodec
     */
    void setProtocolCodec(ProtocolCodec protocolCodec) throws Exception;

    public Executor getBlockingExecutor();
    public void setBlockingExecutor(Executor blockingExecutor);

    ReadableByteChannel getReadChannel();
    WritableByteChannel getWriteChannel();
}
