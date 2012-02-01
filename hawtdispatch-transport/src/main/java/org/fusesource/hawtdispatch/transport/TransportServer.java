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

import java.net.SocketAddress;

import org.fusesource.hawtdispatch.DispatchQueue;

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
    void start(Runnable onComplete) throws Exception;

    /**
     * Stops the service.  Executes the onComplete runnable once the service has fully stopped.
     *
     * @param onComplete my be set to null if not interested in a callback.
     */
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

}
