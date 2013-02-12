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
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.channel.socket.DefaultSocketChannelConfig;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.util.internal.InternalLogger;
import io.netty.util.internal.InternalLoggerFactory;
import org.fusesource.hawtdispatch.DispatchSource;
import org.fusesource.hawtdispatch.Task;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import static java.nio.channels.SelectionKey.*;


/**
 * {@link SocketChannel} implementation which uses HawtDispatch.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class HawtSocketChannel extends HawtAbstractChannel implements SocketChannel {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(HawtSocketChannel.class);
    private static final ChannelMetadata METADATA = new ChannelMetadata(BufType.BYTE, false);

    private final DefaultSocketChannelConfig config;
    private volatile boolean inputShutdown;
    private volatile boolean outputShutdown;
    private DispatchSource readSource;
    private DispatchSource writeSource;

    private static java.nio.channels.SocketChannel newSocket() {
        try {
            return java.nio.channels.SocketChannel.open();
        } catch (IOException e) {
            throw new ChannelException("Failed to open a socket.", e);
        }
    }

    /**
     * Create a new instance
     */
    public HawtSocketChannel() {
        this(newSocket());
    }

    /**
     * Create a new instance using the given {@link java.nio.channels.SocketChannel}.
     */
    public HawtSocketChannel(java.nio.channels.SocketChannel socket) {
        this(null, null, socket);
    }

    /**
     * Create a new instance
     *
     * @param parent the {@link Channel} which created this instance or {@code null} if it was created by the user
     * @param id     the id to use for this instance or {@code null} if a new one should be generated
     * @param socket the {@link java.nio.channels.SocketChannel} which will be used
     */
    public HawtSocketChannel(HawtServerSocketChannel parent, Integer id, java.nio.channels.SocketChannel socket) {
        super(parent, id, socket);
        try {
            socket.configureBlocking(false);
        } catch (IOException e) {
            try {
                socket.close();
            } catch (IOException e2) {
                if (logger.isWarnEnabled()) {
                    logger.warn(
                            "Failed to close a partially initialized socket.", e2);
                }
            }

            throw new ChannelException("Failed to enter non-blocking mode.", e);
        }
        config = new DefaultSocketChannelConfig(this, socket.socket());
    }

    @Override
    public ServerSocketChannel parent() {
        return (ServerSocketChannel) super.parent();
    }

    @Override
    public DefaultSocketChannelConfig config() {
        return config;
    }

    @Override
    public boolean isActive() {
        return ch != null && javaChannel().isOpen() && remoteAddress0() != null;
    }

    @Override
    protected java.nio.channels.SocketChannel javaChannel() {
        return (java.nio.channels.SocketChannel) super.javaChannel();
    }

    @Override
    public ChannelMetadata metadata() {
        return METADATA;
    }

    @Override
    public boolean isInputShutdown() {
        return inputShutdown;
    }

    /**
     * Shutdown the input of this {@link Channel}.
     */
    void setInputShutdown() {
        inputShutdown = true;
    }

    @Override
    public boolean isOutputShutdown() {
        return outputShutdown;
    }

    @Override
    public ChannelFuture shutdownOutput() {
        return shutdownOutput(newPromise());
    }

    @Override
    public ChannelFuture shutdownOutput(final ChannelPromise promise) {
        EventLoop loop = eventLoop();
        if (loop.inEventLoop()) {
            boolean success = false;
            try {
                javaChannel().socket().shutdownOutput();
                success = true;
                promise.setSuccess();
            } catch (Throwable t) {
                promise.setFailure(t);
            } finally {
                if (success) {
                    outputShutdown = true;
                }
            }
        } else {
            loop.execute(new Runnable() {
                @Override
                public void run() {
                    shutdownOutput(promise);
                }
            });
        }
        return promise;
    }

    @Override
    protected boolean doConnect(SocketAddress remoteAddress, SocketAddress localAddress) throws Exception {
        if (localAddress != null) {
            javaChannel().socket().bind(localAddress);
        }

        boolean success = false;
        try {
            boolean connected = javaChannel().connect(remoteAddress);
            if (!connected) {
                // Hook into the CONNECT event..
                final DispatchSource connectSource = createSource(OP_CONNECT);

                // This gets triggered when the socket is connected..
                connectSource.setEventHandler(new Task() {
                    @Override
                    public void run() {
                        ((HawtUnsafe) unsafe()).finishConnect();
                    }
                });
                // enable the delivery of the connect events.
                connectSource.resume();
            }
            success = true;
            return connected;
        } finally {
            if (!success) {
                doClose();
            }
        }
    }

    @Override
    protected InetSocketAddress localAddress0() {
        if (ch == null) {
            return null;
        }
        return (InetSocketAddress) javaChannel().socket().getLocalSocketAddress();
    }

    @Override
    protected InetSocketAddress remoteAddress0() {
        if (ch == null) {
            return null;
        }
        return (InetSocketAddress) javaChannel().socket().getRemoteSocketAddress();
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        javaChannel().socket().bind(localAddress);
    }

    @Override
    protected void doDisconnect() throws Exception {
        doClose();
    }

    @Override
    protected void doClose() throws Exception {
        super.doClose();
        javaChannel().close();
        inputShutdown = true;
        outputShutdown = true;
    }

    @Override
    protected boolean isFlushPending() {
        return false;
    }

    @Override
    protected void doFlushByteBuffer(ByteBuf buf) throws Exception {
        if (!buf.isReadable()) {
            // Reset reader/writerIndex to 0 if the buffer is empty.
            buf.clear();
            return;
        }

        for (int i = config().getWriteSpinCount() - 1; i >= 0; i--) {
            int localFlushedAmount = doWriteBytes(buf, i == 0);
            if (localFlushedAmount > 0) {
                break;
            }
            if (!buf.isReadable()) {
                // Reset reader/writerIndex to 0 if the buffer is empty.
                buf.clear();
                break;
            }
        }
    }

    protected int doWriteBytes(ByteBuf buf, boolean lastSpin) throws Exception {
        final int expectedWrittenBytes = buf.readableBytes();
        final int writtenBytes = buf.readBytes(javaChannel(), expectedWrittenBytes);

        if (writtenBytes >= expectedWrittenBytes) {
            // Wrote the outbound buffer completely - clear OP_WRITE.
            writeSource.suspend();

        } else {
            // Wrote something or nothing.
            // a) If wrote something, the caller will not retry.
            //    - Set OP_WRITE so that the event loop calls flushForcibly() later.
            // b) If wrote nothing:
            //    1) If 'lastSpin' is false, the caller will call this method again real soon.
            //       - Do not update OP_WRITE.
            //    2) If 'lastSpin' is true, the caller will not retry.
            //       - Set OP_WRITE so that the event loop calls flushForcibly() later.
            if (writtenBytes > 0 || lastSpin) {
                writeSource.resume();
            }
        }

        return writtenBytes;
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
                // create the sources and set the event handlers
                readSource = createSource(OP_READ);
                readSource.setEventHandler(new Task() {
                    @Override
                    public void run() {
                        onReadReady();
                    }
                });
                writeSource = createSource(OP_WRITE);
                writeSource.setEventHandler(new Task() {
                    @Override
                    public void run() {
                        unsafe().flushNow();
                    }
                });
                closeFuture().addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        readSource.cancel();
                        writeSource.cancel();
                    }
                });
            }
        };
    }

    @Override
    protected void doBeginRead() throws Exception {
        assert readSource != null;
        if (readSource.isSuspended() && !readSource.isCanceled()) {
            readSource.resume();
        }
    }

    private void onReadReady() {
        final ChannelPipeline pipeline = pipeline();
        final ByteBuf byteBuf = pipeline.inboundByteBuffer();
        boolean closed = false;
        boolean read = false;
        boolean firedInboundBufferSuspended = false;
        try {
            expandReadBuffer(byteBuf);
            loop:
            for (;;) {

                int localReadAmount = byteBuf.writeBytes(javaChannel(), byteBuf.writableBytes());
                if (localReadAmount > 0) {
                    read = true;
                } else if (localReadAmount < 0) {
                    closed = true;
                    break;
                }

                switch (expandReadBuffer(byteBuf)) {
                    case 0:
                        // Read all - stop reading.
                        break loop;
                    case 1:
                        // Keep reading until everything is read.
                        break;
                    case 2:
                        // Let the inbound handler drain the buffer and continue reading.
                        if (read) {
                            read = false;
                            pipeline.fireInboundBufferUpdated();
                            if (!byteBuf.isWritable()) {
                                throw new IllegalStateException(
                                        "an inbound handler whose buffer is full must consume at " +
                                                "least one byte.");
                            }
                        }
                }
            }
        } catch (Throwable t) {
            if (read) {
                read = false;
                pipeline.fireInboundBufferUpdated();
            }

            if (t instanceof IOException) {
                closed = true;
            } else if (!closed) {
                firedInboundBufferSuspended = true;
                pipeline.fireChannelReadSuspended();
            }
            pipeline().fireExceptionCaught(t);
        } finally {
            if (read) {
                pipeline.fireInboundBufferUpdated();
            }

            if (closed) {
                setInputShutdown();
                if (isOpen()) {
                    if (Boolean.TRUE.equals(config().getOption(ChannelOption.ALLOW_HALF_CLOSURE))) {
                        pipeline.fireUserEventTriggered(ChannelInputShutdownEvent.INSTANCE);
                    } else {
                        close(newPromise());
                    }
                }
            } else if (!firedInboundBufferSuspended) {
                pipeline.fireChannelReadSuspended();
            }

            if (!config().isAutoRead()) {
                readSource.suspend();
            }
        }
    }

    @Override
    protected void doFinishConnect() throws Exception {
        if (!javaChannel().finishConnect()) {
            throw new Error();
        }
    }
}
