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

import java.net.SocketAddress;
import java.util.concurrent.Executor;

import org.fusesource.hawtdispatch.DispatchQueue;
import org.fusesource.hawtdispatch.Task;

/**
 * A TransportServer asynchronously accepts {@see Transport} objects and then
 * delivers those objects to a {@see TransportAcceptListener}.
 * 
 * @version $Revision: 1.4 $
 */
public interface TransportServer {
    /**
     * Starts the service.  Executes the onComplete runnable once the service has fully started up.
     *
     * @param onComplete my be set to null if not interested in a callback.
     */
    void start(Task onComplete) throws Exception;
    void start(Runnable onComplete) throws Exception;

    /**
     * Stops the service.  Executes the onComplete runnable once the service has fully stopped.
     *
     * @param onComplete my be set to null if not interested in a callback.
     */
    void stop(Task onComplete) throws Exception;
    void stop(Runnable onComplete) throws Exception;

    /**
     * Registers an {@see TransportAcceptListener} which is notified of accepted
     * channels.
     * 
     * @param acceptListener
     */
    void setTransportServerListener(TransportServerListener acceptListener);

    String getBoundAddress();

    /**
     * @return The socket address that this transport is accepting connections
     *         on or null if this does not or is not currently accepting
     *         connections on a socket.
     */
    SocketAddress getSocketAddress();

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
     * suspend accepting new transports
     */
    void suspend();

    /**
     * resume accepting new transports
     */
    void resume();

    public Executor getBlockingExecutor();

    public void setBlockingExecutor(Executor blockingExecutor);

}
