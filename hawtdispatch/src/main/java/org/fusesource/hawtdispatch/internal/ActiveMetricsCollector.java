/**
 * Copyright (C) 2012 FuseSource, Inc.
 * http://fusesource.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.fusesource.hawtdispatch.internal;

import org.fusesource.hawtdispatch.DispatchQueue;
import org.fusesource.hawtdispatch.Metrics;
import org.fusesource.hawtdispatch.Task;

import java.util.concurrent.atomic.AtomicLong;

/**
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 *
 */
final public class ActiveMetricsCollector extends MetricsCollector {

    private final DispatchQueue queue;

    private final AtomicLong max_run_time = new AtomicLong();
    private final AtomicLong max_wait_time = new AtomicLong();
    private final AtomicLong enqueued = new AtomicLong();
    private final AtomicLong dequeued = new AtomicLong();
    private final AtomicLong total_run_time = new AtomicLong();
    private final AtomicLong total_wait_time = new AtomicLong();
    private final AtomicLong reset_at = new AtomicLong(System.nanoTime());

    public ActiveMetricsCollector(DispatchQueue queue) {
        this.queue = queue;
    }

    private void setMax(AtomicLong holder, long value) {
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

    public Task track(final Task runnable) {
        enqueued.incrementAndGet();
        final long enqueuedAt = System.nanoTime();
        return new Task(){
            public void run() {
                long dequeued_at = System.nanoTime();
                long wait_time = dequeued_at - enqueuedAt;
                total_wait_time.addAndGet(wait_time);
                setMax(max_wait_time,wait_time );
                dequeued.incrementAndGet();
                long dequeuedAt = dequeued_at;
                try {
                    runnable.run();
                } finally {
                    long run_time = System.nanoTime() - dequeuedAt;
                    total_run_time.addAndGet(run_time);
                    setMax(max_run_time,run_time);
                }
            }
        };
    }
    
    public Metrics metrics() {
        long now = System.nanoTime();
        long start = reset_at.getAndSet(now);
        long enq = enqueued.getAndSet(0);
        long deq = dequeued.getAndSet(0);
        if( enq==0 && deq==0 ) {
            return null;
        }
        Metrics rc = new Metrics();
        rc.durationNS = now-start;
        rc.queue = queue;
        rc.enqueued = enq;
        rc.dequeued = deq;
        rc.maxWaitTimeNS = max_wait_time.getAndSet(0);
        rc.maxRunTimeNS = max_run_time.getAndSet(0);
        rc.totalRunTimeNS = total_run_time.getAndSet(0);
        rc.totalWaitTimeNS = total_wait_time.getAndSet(0);
        return rc;
    }

}
