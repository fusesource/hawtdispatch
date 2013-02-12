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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link AbstractHawtEventLoopGroup} implementation which use pre-created shared serial {@link DispatchQueue}s
 * between the registered {@link Channel}s.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class SharedHawtEventLoopGroup extends AbstractHawtEventLoopGroup {

    private static final AtomicInteger poolId = new AtomicInteger();

    private final EventLoop[] children;
    private final AtomicInteger childIndex = new AtomicInteger();

    /**
     * Create a new instance
     *
     * @param queue         the {@link DispatchQueue} from which the serial {@link DispatchQueue}s will be created.
     * @param queueNumber   the number of serial {@link DispatchQueue}s created from the given {@link DispatchQueue}
     */
    public SharedHawtEventLoopGroup(DispatchQueue queue, int queueNumber) {
        if (queueNumber < 1) {
            throw new IllegalArgumentException("queueNumber must be >= 1");
        }
        if (queue == null) {
            throw new NullPointerException("queue");
        }
        children = new EventLoop[queueNumber];
        for (int i = 0; i < queueNumber; i++) {
            children[i] = new HawtEventLoop(this, queue.createQueue(poolId.get() + "-" + i));
        }
    }

    @Override
    public EventLoop next() {
        return children[Math.abs(childIndex.getAndIncrement() % children.length)];
    }

    @Override
    protected Set<EventExecutor> children() {
        Set<EventExecutor> children = Collections.newSetFromMap(new LinkedHashMap<EventExecutor, Boolean>());
        Collections.addAll(children, this.children);
        return children;
    }
}
