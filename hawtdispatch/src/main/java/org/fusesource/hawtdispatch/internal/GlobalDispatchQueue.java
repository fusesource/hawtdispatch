/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fusesource.hawtdispatch.internal;

import java.util.concurrent.TimeUnit;

import org.fusesource.hawtdispatch.DispatchPriority;
import org.fusesource.hawtdispatch.DispatchQueue;
import org.fusesource.hawtdispatch.internal.pool.SimplePool;
import org.fusesource.hawtdispatch.internal.util.QueueSupport;
import org.fusesource.hawtdispatch.internal.util.IntrospectionSupport;

/**
 * 
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
final public class GlobalDispatchQueue implements HawtDispatchQueue {

    private final HawtDispatcher dispatcher;
    final String label;
    private final DispatchPriority priority;

    final WorkerPool workers;

    public GlobalDispatchQueue(HawtDispatcher dispatcher, DispatchPriority priority, int threads) {
        this.dispatcher = dispatcher;
        this.priority = priority;
        this.label=priority.toString();
//        this.workers = new StealingPool(dispatcher.getLabel()+"-"+priority, threads, priority(priority));
        this.workers = new SimplePool(dispatcher.getLabel()+"-"+priority, threads, priority(priority));
    }

    static private int priority(DispatchPriority priority) {
        switch(priority) {
            case HIGH:
                return Thread.MAX_PRIORITY;
            case DEFAULT:
                return Thread.NORM_PRIORITY;
            case LOW:
                return Thread.MIN_PRIORITY;
        }
        return 0;
    }

    public HawtDispatcher getDispatcher() {
        return dispatcher;
    }

    public String getLabel() {
        return label;
    }

    public void execute(Runnable runnable) {
        dispatchAsync(runnable);
    }

    public void dispatchAsync(Runnable runnable) {
        workers.execute(runnable);
    }

    public void dispatchAfter(long delay, TimeUnit unit, Runnable runnable) {
        dispatcher.timerThread.addRelative(runnable, this, delay, unit);
    }

    public void dispatchSync(final Runnable runnable) throws InterruptedException {
        dispatchApply(1, runnable);
    }
    
    public void dispatchApply(int iterations, final Runnable runnable) throws InterruptedException {
        QueueSupport.dispatchApply(this, iterations, runnable);
    }

    public ThreadDispatchQueue getTargetQueue() {
        return null;
    }

    public DispatchPriority getPriority() {
        return priority;
    }

    public void release() {
    }

    public void retain() {
    }

    public void resume() {
        throw new UnsupportedOperationException();
    }

    public void suspend() {
        throw new UnsupportedOperationException();
    }

    public boolean isSuspended() {
        throw new UnsupportedOperationException();
    }

    public <Context> Context getContext() {
        throw new UnsupportedOperationException();
    }

    public <Context> void setContext(Context context) {
        throw new UnsupportedOperationException();
    }

    public void addReleaseWatcher(Runnable finalizer) {
        throw new UnsupportedOperationException();
    }

    public void setTargetQueue(DispatchQueue queue) {
        throw new UnsupportedOperationException();
    }

    public GlobalDispatchQueue isGlobalDispatchQueue() {
        return this;
    }

    public SerialDispatchQueue isSerialDispatchQueue() {
        return null;
    }

    public ThreadDispatchQueue isThreadDispatchQueue() {
        return null;
    }

    @Override
    public String toString() {
        return IntrospectionSupport.toString(this);
    }

    public boolean isReleased() {
        return false;
    }

    public DispatchQueue createSerialQueue(String label) {
        DispatchQueue rc = dispatcher.createQueue(label);
        rc.setTargetQueue(this);
        return rc;
    }

    public QueueType getQueueType() {
        return QueueType.GLOBAL_QUEUE;
    }
}
