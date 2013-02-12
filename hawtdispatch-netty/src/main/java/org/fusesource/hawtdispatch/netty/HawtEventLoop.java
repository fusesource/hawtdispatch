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
import org.fusesource.hawtdispatch.DispatchPriority;
import org.fusesource.hawtdispatch.DispatchQueue;
import org.fusesource.hawtdispatch.internal.ThreadDispatchQueue;

import java.util.*;
import java.util.concurrent.*;

/**
 * {@link EventLoop} implementations which will
 * handle HawtDispatch based {@link Channel}s.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
final class HawtEventLoop extends AbstractExecutorService implements EventLoop {

    private final EventLoopGroup parent;
    private volatile boolean shutdown;
    private final DispatchQueue queue;

    HawtEventLoop(EventLoopGroup parent, DispatchQueue queue) {
        if (parent == null) {
            throw new NullPointerException("parent");
        }
        if (queue == null) {
            throw new NullPointerException("queue");
        }
        this.parent = parent;
        this.queue = queue;
    }

    /**
     * Returns the backing {@link DispatchQueue}
     */
    DispatchQueue queue() {
        return queue;
    }

    @Override
    public EventLoopGroup parent() {
        return parent;
    }

    @Override
    public boolean inEventLoop() {
        return queue.isExecuting();
    }

    @Override
    public EventLoop next() {
        return this;
    }

    @Override
    public void shutdown() {
        shutdown = true;
    }

    @Override
    public List<Runnable> shutdownNow() {
        shutdown = true;
        return Collections.emptyList();
    }

    @Override
    public boolean isShutdown() {
        return shutdown;
    }

    @Override
    public boolean isTerminated() {
        return shutdown;
    }

    @Override
    public boolean awaitTermination(long l, TimeUnit timeUnit) throws InterruptedException {
        return shutdown;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> vCallable, long delay, TimeUnit timeUnit) {
        return new ScheduledFutureTask(vCallable, timeUnit.toNanos(delay)).schedule();
    }

    @Override
    public ScheduledFuture<?> schedule(final Runnable runnable, long delay, TimeUnit timeUnit) {
        return new ScheduledFutureTask(runnable, timeUnit.toNanos(delay)).schedule();
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable runnable, long delay, long period, TimeUnit timeUnit) {
        return new ScheduledFutureTask(runnable, timeUnit.toNanos(delay), timeUnit.toNanos(period)).schedule();
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable runnable, long initialDelay, long delay, TimeUnit timeUnit) {
        throw new UnsupportedOperationException();
    }

    private final class ScheduledFutureTask<V> extends FutureTask<V> implements ScheduledFuture<V> {

        private long deadlineNanos;
        private final long periodNanos;

        ScheduledFutureTask(Runnable runnable,long nanoTime) {
            super(runnable, null);
            deadlineNanos = nanoTime;
            periodNanos = 0;
        }

        ScheduledFutureTask(Runnable runnable, long nanoTime, long period) {
            super(runnable, null);
            if (period == 0) {
                throw new IllegalArgumentException("period: 0 (expected: != 0)");
            }
            deadlineNanos = nanoTime;
            periodNanos = period;
        }

        ScheduledFutureTask(Callable<V> callable, long nanoTime) {
            super(callable);
            deadlineNanos = nanoTime;
            periodNanos = 0;
        }

        public long delayNanos() {
            return Math.max(0, deadlineNanos - System.nanoTime());
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(delayNanos(), TimeUnit.NANOSECONDS);
        }

        @Override
        public int compareTo(Delayed o) {
            if (this == o) {
                return 0;
            }
            ScheduledFutureTask<?> that = (ScheduledFutureTask<?>) o;
            long d = deadlineNanos - that.deadlineNanos;
            if (d < 0) {
                return -1;
            } else if (d > 0) {
                return 1;
            } else {
                return 1;
            }
        }

        @Override
        public void run() {
            if (periodNanos == 0) {
                super.run();
            } else {
                boolean reset = runAndReset();
                if (reset && !isShutdown()) {
                    long p = periodNanos;
                    if (p > 0) {
                        deadlineNanos += p;
                    } else {
                        deadlineNanos = System.nanoTime() - p;
                    }
                    schedule();
                }
            }
        }

        ScheduledFuture<V> schedule() {
            queue.executeAfter(delayNanos(), TimeUnit.NANOSECONDS, this);
            return this;
        }
    }

    @Override
    public void execute(Runnable runnable) {
        queue.execute(runnable);
    }

    @Override
    public boolean inEventLoop(Thread thread) {
        for (DispatchQueue queue: Dispatch.getThreadQueues(DispatchPriority.DEFAULT)) {
            if (thread == ((ThreadDispatchQueue) queue).getThread()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ChannelFuture register(Channel channel) {
        if (channel == null) {
            throw new NullPointerException("channel");
        }
        return register(channel, channel.newPromise());
    }

    @Override
    public ChannelFuture register(final Channel channel, final ChannelPromise promise) {
        if (isShutdown()) {
            channel.unsafe().closeForcibly();
            promise.setFailure(new EventLoopException("cannot register a channel to a shut down loop"));
            return promise;
        }

        if (inEventLoop()) {
            channel.unsafe().register(this, promise);
        } else {
            execute(new Runnable() {
                @Override
                public void run() {
                    channel.unsafe().register(HawtEventLoop.this, promise);
                }
            });
        }

        return promise;
    }

}
