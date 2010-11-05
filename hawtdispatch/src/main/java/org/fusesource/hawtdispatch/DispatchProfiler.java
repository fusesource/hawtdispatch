/**
 *  Copyright (C) 2009, Progress Software Corporation and/or its
 * subsidiaries or affiliates.  All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fusesource.hawtdispatch;

import java.util.ArrayList;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;


/**
 * <p>
 * </p>
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public class DispatchProfiler {

    static public class DispatchMetrics {
        public String label;
        public long enqueued;
        public long dequeued;
        public long max_wait_time_ns;
        public long max_run_time_ns;
        public long total_run_time_ns;
        public long total_wait_time_ns;

        @Override
        public String toString() {
            return String.format("{ queue:%s, enqueued:%d, dequeued:%d, max_wait_time:%.2f ms, max_run_time:%.2f ms, total_run_time:%.2f ms, total_wait_time:%.2f ms }",
                    label,
                    enqueued,
                    dequeued,
                    max_wait_time_ns /1000000.0f,
                    max_run_time_ns /1000000.0f,
                    total_run_time_ns /1000000.0f,
                    total_wait_time_ns /1000000.0f);
        }
    }

    static private class ProfiledQueue implements DispatchQueue {

        private final DispatchQueue queue;

        private final AtomicLong max_run_time = new AtomicLong();
        private final AtomicLong max_wait_time = new AtomicLong();
        private final AtomicLong enqueued = new AtomicLong();
        private final AtomicLong dequeued = new AtomicLong();
        private final AtomicLong total_run_time = new AtomicLong();
        private final AtomicLong total_wait_time = new AtomicLong();

        ProfiledQueue(DispatchQueue queue) {
            this.queue = queue;
        }

        public void setMax(AtomicLong holder, long value) {
            while (true) {
                long p = holder.get();
                if( value > p ) {
                    if( holder.compareAndSet(p, value) ) {
                        return;
                    }
                } else {
                    return;
                }
            }
        }

        public void dispatchAsync(final Runnable runnable) {
            final long enqueued_at = System.nanoTime();
            enqueued.incrementAndGet();
            queue.dispatchAsync(new Runnable(){
                public void run() {
                    long dequeued_at = System.nanoTime();
                    long wait_time = dequeued_at - enqueued_at;
                    total_wait_time.addAndGet(wait_time);
                    setMax(max_wait_time,wait_time );
                    dequeued.incrementAndGet();
                    try {
                        runnable.run();
                    } finally {
                        long run_time = System.nanoTime() - dequeued_at;
                        total_run_time.addAndGet(run_time);
                        setMax(max_run_time,run_time);
                    }
                }
            });
        }

        DispatchMetrics metrics() {
            DispatchMetrics rc = new DispatchMetrics();
            rc.label = queue.getLabel();
            rc.enqueued = enqueued.getAndSet(0);;
            rc.dequeued = dequeued.getAndSet(0);;
            rc.max_wait_time_ns = max_wait_time.getAndSet(0);;
            rc.max_run_time_ns = max_run_time.getAndSet(0);;
            rc.total_run_time_ns = total_run_time.getAndSet(0);
            rc.total_wait_time_ns = total_wait_time.getAndSet(0);
            return rc;
        }

        public void dispatchAfter(long delay, TimeUnit unit, final Runnable runnable) {
            queue.dispatchAfter(delay, unit, runnable);
        }

        public DispatchQueue createSerialQueue(String label) {
            return queue.createSerialQueue(label);
        }

        public void execute(Runnable runnable) {
            dispatchAsync(runnable);
        }

        public String getLabel() {
            return queue.getLabel();
        }

        public void setLabel(String label) {
            queue.setLabel(label);
        }

        public DispatchQueue.QueueType getQueueType() {
            return queue.getQueueType();
        }

        public boolean isExecuting() {
            return queue.isExecuting();
        }

        public DispatchQueue getTargetQueue() {
            return queue.getTargetQueue();
        }

        public void setTargetQueue(DispatchQueue queue) {
            this.queue.setTargetQueue(queue);
        }

        public boolean isSuspended() {
            return queue.isSuspended();
        }

        public void resume() {
            queue.resume();
        }

        public void suspend() {
            queue.suspend();
        }

        public void release() {
            queue.release();
        }

        public void retain() {
            queue.retain();
        }

        public int retained() {
            return queue.retained();
        }

        public void setDisposer(Runnable disposer) {
            queue.setDisposer(disposer);
        }

    }

    final static public WeakHashMap<ProfiledQueue, Object> queues = new WeakHashMap<ProfiledQueue, Object>();

    static public DispatchQueue profile(DispatchQueue queue) {
        if( queue instanceof ProfiledQueue ) {
            return queue;
        }

        ProfiledQueue rc = new ProfiledQueue(queue);
        synchronized (queues) {
            queues.put(rc, "");
        }
        return rc;
    }

    static public DispatchQueue unwrap(DispatchQueue queue) {
        if( queue instanceof ProfiledQueue ) {
            return ((ProfiledQueue)queue).queue;
        }
        return queue;
    }

    static public DispatchQueue unprofile(DispatchQueue queue) {
        DispatchQueue rc = null;
        if( queue instanceof ProfiledQueue ) {
            rc = ((ProfiledQueue)queue).queue;
            synchronized (queues) {
                queues.remove(rc);
            }
        }
        return rc;
    }

    public static ArrayList<DispatchMetrics> metrics() {
        synchronized (queues) {
            ArrayList<DispatchMetrics> rc = new ArrayList<DispatchMetrics>();
            for( ProfiledQueue queue : queues.keySet() ) {
                if( queue!=null ) {
                    DispatchMetrics metric = queue.metrics();
                    if( metric.enqueued!=0 || metric.dequeued!=0 ) {
                        rc.add(metric);
                    }
                }
            }
            return rc;
        }
    }

}
