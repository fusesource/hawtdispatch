/*
 * Copyright 2012 The Netty Project
 * Copyright 2013 Red Hat, Inc.
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.fusesource.hawtdispatch.netty;

import io.netty.channel.*;
import org.fusesource.hawtdispatch.Dispatch;
import org.fusesource.hawtdispatch.DispatchQueue;
import org.fusesource.hawtdispatch.DispatchSource;

import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Abstract base class for {@link Channel} implementations that use
 * HawtDispatch.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
abstract class HawtAbstractChannel extends AbstractChannel {

    protected volatile SelectableChannel ch;

    /**
     * The future of the current connection attempt.  If not null, subsequent
     * connection attempts will fail.
     */
    private ChannelPromise connectPromise;
    private ScheduledFuture<?> connectTimeoutFuture;
    private ConnectException connectTimeoutException;
    /**
     * Creates a new instance.
     *
     * @param id
     *        the unique non-negative integer ID of this channel.
     *        Specify {@code null} to auto-generate a unique negative integer
     *        ID.
     * @param parent
     *        the parent of this channel. {@code null} if there's no parent.
     * @param ch
     *        the {@link SocketChannel} which will handle the IO or {@code null} if not created yet.
     */
    protected HawtAbstractChannel(Channel parent, Integer id, SelectableChannel ch) {
        super(parent, id);
        this.ch = ch;
    }

    @Override
    public InetSocketAddress localAddress() {
        return (InetSocketAddress) super.localAddress();
    }

    @Override
    public InetSocketAddress remoteAddress() {
        return (InetSocketAddress) super.remoteAddress();
    }


    /**
     * Return the underlying {@link SocketChannel}. Be aware this should only be called after it was set as
     * otherwise it will throw an {@link IllegalStateException}.
     */
    protected SelectableChannel javaChannel() {
        if (ch == null) {
            throw new IllegalStateException("Try to access Channel before eventLoop was registered");
        }
        return ch;
    }

    @Override
    public boolean isOpen() {
        if (ch == null) {
            return true;
        }
        return ch.isOpen();
    }

    @Override
    protected boolean isCompatible(EventLoop loop) {
        return loop instanceof HawtEventLoop;
    }

    @Override
    protected AbstractUnsafe newUnsafe() {
        return new HawtUnsafe();
    }

    protected final DispatchSource createSource(int op) {
        return Dispatch.createSource(javaChannel(), op, getDispatchQueue());
    }

    public DispatchQueue getDispatchQueue() {
        return ((HawtEventLoop) eventLoop()).queue();
    }

    /**
     * Connect to the remote peer using the given localAddress if one is specified or {@code null} otherwise.
     */
    protected abstract boolean doConnect(SocketAddress remoteAddress,
            SocketAddress localAddress) throws Exception;

    /**
     * Finish the connect
     */
    protected abstract void doFinishConnect() throws Exception;

    @Override
    protected void doClose() throws Exception {
        if (connectTimeoutFuture != null) {
            connectTimeoutFuture.cancel(false);
        }
    }



    final class HawtUnsafe extends AbstractUnsafe {

        @Override
        public void connect(
                final SocketAddress remoteAddress, final SocketAddress localAddress, final ChannelPromise promise) {
            if (eventLoop().inEventLoop()) {
                if (!ensureOpen(promise)) {
                    return;
                }
                try {
                    if (connectPromise != null) {
                        throw new IllegalStateException("connection attempt already made");
                    }

                    boolean wasActive = isActive();
                    if (doConnect(remoteAddress, localAddress)) {
                        promise.setSuccess();
                        if (!wasActive && isActive()) {
                            pipeline().fireChannelActive();
                        }
                    } else {
                        connectPromise = promise;

                        // Schedule connect timeout.
                        int connectTimeoutMillis = config().getConnectTimeoutMillis();
                        if (connectTimeoutMillis > 0) {
                            connectTimeoutFuture = eventLoop().schedule(new Runnable() {
                                @Override
                                public void run() {
                                    if (connectTimeoutException == null) {
                                        connectTimeoutException = new ConnectException("connection timed out");
                                    }
                                    ChannelPromise connectPromise = HawtAbstractChannel.this.connectPromise;
                                    if (connectPromise != null && connectPromise.tryFailure(connectTimeoutException)) {
                                        close(voidFuture());
                                    }
                                }
                            }, connectTimeoutMillis, TimeUnit.MILLISECONDS);
                        }
                    }
                } catch (Throwable t) {
                    promise.setFailure(t);
                    closeIfClosed();
                }
            } else {
                eventLoop().execute(new Runnable() {
                    @Override
                    public void run() {
                        connect(remoteAddress, localAddress, promise);
                    }
                });
            }
        }

        /**
         * Finish connect operation
         */
        public void finishConnect() {
            assert eventLoop().inEventLoop();
            assert connectPromise != null;
            try {
                boolean wasActive = isActive();
                doFinishConnect();
                connectPromise.setSuccess();
                if (!wasActive && isActive()) {
                    pipeline().fireChannelActive();
                }
            } catch (Throwable t) {
                connectPromise.tryFailure(t);
                closeIfClosed();
            } finally {
                connectTimeoutFuture.cancel(false);
                connectPromise = null;
            }
        }
    }
}
