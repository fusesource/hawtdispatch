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

import io.netty.channel.AbstractChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;

import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Abstract base class for {@link io.netty.channel.Channel} implementations that use
 * HawtDispatch.
 */
abstract class HawtAbstractChannel extends AbstractChannel {

    protected volatile java.nio.channels.Channel ch;

    /**
     * The future of the current connection attempt.  If not null, subsequent
     * connection attempts will fail.
     */
    protected ScheduledFuture<?> connectTimeoutFuture;
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
    protected HawtAbstractChannel(Channel parent, Integer id, java.nio.channels.Channel ch) {
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
    protected java.nio.channels.Channel javaChannel() {
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
        return new HawtAbstractUnsafe();
    }

    /**
     * Connect to the remote peer using the given localAddress if one is specified or {@code null} otherwise.
     */
    protected abstract void doConnect(SocketAddress remoteAddress,
            SocketAddress localAddress, ChannelPromise connectPromise);

    class HawtAbstractUnsafe extends AbstractUnsafe {
        @Override
        public void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
            doConnect(remoteAddress, localAddress, promise);
        }
    }
}
