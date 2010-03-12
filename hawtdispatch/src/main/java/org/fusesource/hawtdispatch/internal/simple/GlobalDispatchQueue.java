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
package org.fusesource.hawtdispatch.internal.simple;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.fusesource.hawtdispatch.DispatchOption;
import org.fusesource.hawtdispatch.DispatchPriority;
import org.fusesource.hawtdispatch.DispatchQueue;
import org.fusesource.hawtdispatch.internal.QueueSupport;
import org.fusesource.hawtdispatch.internal.util.IntrospectionSupport;

/**
 * 
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
final public class GlobalDispatchQueue implements SimpleQueue {

    private final SimpleDispatcher dispatcher;
    final String label;
    final ConcurrentLinkedQueue<Runnable> globalRunnables = new ConcurrentLinkedQueue<Runnable>();
    final AtomicLong counter;
    private final DispatchPriority priority;

    public GlobalDispatchQueue(SimpleDispatcher dispatcher, DispatchPriority priority) {
        this.dispatcher = dispatcher;
        this.priority = priority;
        this.label=priority.toString();
        this.counter = dispatcher.globalQueuedRunnables;
    }

    public String getLabel() {
        return label;
    }

    public void execute(Runnable runnable) {
        dispatchAsync(runnable);
    }

    public void dispatchAsync(Runnable runnable) {
//        DispatcherThread thread = DispatcherThread.currentDispatcherThread();
//        if( runnable.getClass() == SerialDispatchQueue.class ) {
//            SerialDispatchQueue queue = ((SerialDispatchQueue)runnable);
//            queue.pick(this, thread);
//        } else {
//            if( thread==null ) {
                enqueueExternal(runnable);
//            } else {
//                thread.currentThreadQueue.localEnqueue(runnable);
//            }
//        }
    }

    void enqueueExternal(Runnable runnable) {
        this.counter.incrementAndGet();
        globalRunnables.add(runnable);
        dispatcher.wakeup();
    }

    public void dispatchAfter(Runnable runnable, long delay, TimeUnit unit) {
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
    
    public Runnable poll() {
        Runnable rc = globalRunnables.poll();
        if( rc !=null ) {
            counter.decrementAndGet();
        }
        return rc;
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

    public Set<DispatchOption> getOptions() {
        return Collections.emptySet();
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

    public DispatchQueue createSerialQueue(String label, DispatchOption... options) {
        DispatchQueue rc = dispatcher.createSerialQueue(label, options);
        rc.setTargetQueue(this);
        return rc;
    }

    public QueueType getQueueType() {
        return QueueType.GLOBAL_QUEUE;
    }
}
