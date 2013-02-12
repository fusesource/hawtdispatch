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
import org.fusesource.hawtdispatch.DispatchQueue;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link HawtEventLoopGroup} implementation which will handle
 * AIO {@link Channel} implementations.
 *
 */
public class HawtEventLoopGroup implements EventLoopGroup {

    public static final int DEFAULT_POOL_SIZE = Runtime.getRuntime().availableProcessors() * 2;
    private static final AtomicInteger poolId = new AtomicInteger();

    private final EventLoop[] children;
    private final AtomicInteger childIndex = new AtomicInteger();

    public HawtEventLoopGroup(int nThreads, DispatchQueue queue) {
        children = new EventLoop[nThreads];
        for (int i = 0; i < nThreads; i++) {
            children[i] = new HawtEventLoop(this, queue.createQueue(poolId.get() + "-" + i));
        }
    }

    public HawtEventLoopGroup(DispatchQueue[] queues) {
        children = new EventLoop[queues.length];
        for (int i = 0; i < queues.length; i++) {
            children[i] = new HawtEventLoop(this, queues[i]);
        }
    }

    @Override
    public EventLoop next() {
        return children[Math.abs(childIndex.getAndIncrement() % children.length)];
    }

    @Override
    public ChannelFuture register(Channel channel) {
        return next().register(channel);
    }

    @Override
    public ChannelFuture register(Channel channel, ChannelPromise promise) {
        return next().register(channel, promise);
    }

    /**
     * Return a safe-copy of all of the children of this group.
     */
    protected Set<EventExecutor> children() {
        Set<EventExecutor> children = Collections.newSetFromMap(new LinkedHashMap<EventExecutor, Boolean>());
        Collections.addAll(children, this.children);
        return children;
    }

    @Override
    public void shutdown() {
        if (isShutdown()) {
            return;
        }

        for (EventExecutor l: children) {
            l.shutdown();
        }
    }

    @Override
    public boolean isShutdown() {
        for (EventExecutor l: children) {
            if (!l.isShutdown()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isTerminated() {
        for (EventExecutor l: children) {
            if (!l.isTerminated()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit)
            throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        loop: for (EventExecutor l: children) {
            for (;;) {
                long timeLeft = deadline - System.nanoTime();
                if (timeLeft <= 0) {
                    break loop;
                }
                if (l.awaitTermination(timeLeft, TimeUnit.NANOSECONDS)) {
                    break;
                }
            }
        }
        return isTerminated();
    }
}
