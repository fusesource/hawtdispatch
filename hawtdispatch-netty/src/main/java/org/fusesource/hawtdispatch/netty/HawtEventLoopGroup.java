/*
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

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventExecutor;
import io.netty.channel.EventLoop;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import org.fusesource.hawtdispatch.DispatchQueue;

import java.util.HashSet;
import java.util.Set;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link AbstractHawtEventLoopGroup} which will create a new serial {@link DispatchQueue} for every registered
 * {@link Channel}.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class HawtEventLoopGroup extends AbstractHawtEventLoopGroup {
    private final ChannelGroup group = new DefaultChannelGroup();
    private final DispatchQueue queue;
    private final AtomicInteger eventLoopId = new AtomicInteger();

    private final ChannelFutureListener closeListener = new ChannelFutureListener() {

        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            group.remove(future.channel());
        }
    };

    private final ChannelFutureListener registerListener = new ChannelFutureListener() {
        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            if (future.isSuccess() && future.channel().isOpen()) {
                group.add(future.channel());
                future.channel().closeFuture().addListener(closeListener);
            }
        }
    };

    public HawtEventLoopGroup(DispatchQueue queue) {
        if (queue == null) {
            throw new NullPointerException("queue");
        }
        this.queue = queue;
    }

    @Override
    public ChannelFuture register(Channel channel, ChannelPromise promise) {
        ChannelFuture future =  super.register(channel, promise);
        future.addListener(registerListener);
        return future;
    }

    @Override
    protected  Set<EventExecutor> children() {
        Set<EventExecutor> executors = new HashSet<EventExecutor>(group.size());
        for (Channel channel: group) {
            executors.add(channel.eventLoop());
        }
        return executors;
    }

    @Override
    public EventLoop next() {
        return new HawtEventLoop(this, queue.createQueue(HawtEventLoopGroup.class.getSimpleName() + '-' + eventLoopId.incrementAndGet()));
    }
}
