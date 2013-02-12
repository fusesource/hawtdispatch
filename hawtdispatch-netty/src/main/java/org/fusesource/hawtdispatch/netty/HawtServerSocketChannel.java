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

import io.netty.buffer.BufType;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.socket.DefaultServerSocketChannelConfig;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.ServerSocketChannelConfig;
import io.netty.util.internal.InternalLogger;
import io.netty.util.internal.InternalLoggerFactory;
import org.fusesource.hawtdispatch.*;
import static java.nio.channels.SelectionKey.*;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;

/**
 * {@link ServerSocketChannel} implementation which uses HawtDispatch.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class HawtServerSocketChannel extends HawtAbstractChannel implements ServerSocketChannel {

    private static final ChannelMetadata METADATA = new ChannelMetadata(BufType.MESSAGE, false);
    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(HawtServerSocketChannel.class);

    private DispatchSource acceptSource;

    private static java.nio.channels.ServerSocketChannel newSocket() {
        try {
            return java.nio.channels.ServerSocketChannel.open();
        } catch (IOException e) {
            throw new ChannelException(
                    "Failed to open a server socket.", e);
        }
    }

    private final ServerSocketChannelConfig config;

    /**
     * Create a new instance
     */
    public HawtServerSocketChannel() throws IOException {
        super(null, null, newSocket());
        javaChannel().configureBlocking(false);
        config = new DefaultServerSocketChannelConfig(this, javaChannel().socket());
    }

    @Override
    protected java.nio.channels.ServerSocketChannel javaChannel() {
        return (java.nio.channels.ServerSocketChannel) super.javaChannel();
    }

    @Override
    public boolean isActive() {
        return ch != null && javaChannel().isOpen() && localAddress0() != null;
    }

    @Override
    public ChannelMetadata metadata() {
        return METADATA;
    }

    @Override
    protected SocketAddress localAddress0() {
        return javaChannel().socket().getLocalSocketAddress();
    }

    @Override
    protected SocketAddress remoteAddress0() {
        return null;
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        javaChannel().socket().bind(localAddress, config.getBacklog());
    }

    @Override
    protected Runnable doRegister() throws Exception {
        final Runnable task = super.doRegister();

        return new Runnable() {
            @Override
            public void run() {
                if (task != null) {
                    task.run();
                }
                // Create the source and register the handlers to it
                acceptSource = createSource(OP_ACCEPT);
                acceptSource.setEventHandler(new Task() {
                    @Override
                    public void run() {
                        boolean added = false;
                        for (;;) {
                            try {
                                SocketChannel channel = javaChannel().accept();
                                if (channel == null) {
                                    break;
                                }
                                pipeline().inboundMessageBuffer().add(
                                        new HawtSocketChannel(HawtServerSocketChannel.this, null, channel));
                                added = true;

                            } catch (IOException e) {
                                if (isOpen()) {
                                    logger.warn("Failed to create a new channel from an accepted socket.", e);
                                }
                                break;
                            }
                        }
                        if (added) {
                            pipeline().fireInboundBufferUpdated();
                            pipeline().fireChannelReadSuspended();
                        }

                        // suspend accepts if needed
                        if (!config().isAutoRead()) {
                            acceptSource.suspend();
                        }
                    }
                });
                closeFuture().addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        acceptSource.cancel();
                    }
                });
            }
        };

    }

    @Override
    protected void doBeginRead() {
        if (acceptSource.isSuspended() && !acceptSource.isCanceled()) {
            acceptSource.resume();
        }
    }

    @Override
    protected void doClose() throws Exception {
        super.doClose();
        javaChannel().close();
    }

    @Override
    protected boolean isFlushPending() {
        return false;
    }

    @Override
    protected boolean doConnect(
            SocketAddress remoteAddress, SocketAddress localAddress) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void doFinishConnect() throws Exception {
        throw new UnsupportedOperationException();
    }
    @Override
    protected void doDisconnect() throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public ServerSocketChannelConfig config() {
        return config;
    }
}
