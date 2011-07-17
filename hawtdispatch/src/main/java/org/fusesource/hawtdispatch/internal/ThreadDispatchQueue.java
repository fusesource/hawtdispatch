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

import java.util.LinkedList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import org.fusesource.hawtdispatch.DispatchPriority;
import org.fusesource.hawtdispatch.DispatchQueue;
import org.fusesource.hawtdispatch.Metrics;
import org.fusesource.hawtdispatch.internal.util.QueueSupport;

/**
 * 
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
final public class ThreadDispatchQueue implements HawtDispatchQueue {

    volatile String label;

    final LinkedList<Runnable> localRunnables = new LinkedList<Runnable>();
    final ConcurrentLinkedQueue<Runnable> sharedRunnables = new ConcurrentLinkedQueue<Runnable>();
    final WorkerThread thread;
    final GlobalDispatchQueue globalQueue;
    private MetricsCollector metricsCollector = InactiveMetricsCollector.INSTANCE;
    private final LinkedList<Runnable> sourceQueue= new LinkedList<Runnable>();

    public ThreadDispatchQueue(GlobalDispatchQueue globalQueue, WorkerThread thread) {
        this.thread = thread;
        this.globalQueue = globalQueue;
        this.label=thread.getName()+" pritority: "+globalQueue.getLabel();
        getDispatcher().track(this);
    }

    public LinkedList<Runnable> getSourceQueue() {
        return sourceQueue;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public boolean isExecuting() {
        return globalQueue.dispatcher.getCurrentThreadQueue() == this;
    }

    public void assertExecuting() {
        assert isExecuting() : getDispatcher().assertMessage();
    }

    public HawtDispatcher getDispatcher() {
        return globalQueue.dispatcher;
    }

    public void execute(java.lang.Runnable runnable) {
        runnable = metricsCollector.track(runnable);
        // We don't have to take the synchronization hit 
        if( Thread.currentThread()!=thread ) {
            sharedRunnables.add(runnable);
            thread.unpark();
        } else {
            localRunnables.add(runnable);
        }
    }

    public Runnable poll() {
        Runnable rc = localRunnables.poll();
        if (rc ==null) {
            rc = sharedRunnables.poll();
        }
        return rc;
    }

    public void executeAfter(long delay, TimeUnit unit, Runnable runnable) {
        getDispatcher().timerThread.addRelative(runnable, this, delay, unit);
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

    public void setTargetQueue(DispatchQueue queue) {
        throw new UnsupportedOperationException();
    }
    
    public HawtDispatchQueue getTargetQueue() {
        return null;
    }
    
    public DispatchPriority getPriority() {
        return globalQueue.getPriority();
    }

    public GlobalDispatchQueue isGlobalDispatchQueue() {
        return null;
    }

    public SerialDispatchQueue isSerialDispatchQueue() {
        return null;
    }

    public ThreadDispatchQueue isThreadDispatchQueue() {
        return this;
    }

    public DispatchQueue createQueue(String label) {
        DispatchQueue rc = globalQueue.dispatcher.createQueue(label);
        rc.setTargetQueue(this);
        return rc;
    }

    public QueueType getQueueType() {
        return QueueType.THREAD_QUEUE;
    }

    public void profile(boolean on) {
        if( on ) {
            metricsCollector = new ActiveMetricsCollector(this);
        } else {
            metricsCollector = InactiveMetricsCollector.INSTANCE;
        }
    }

    public Metrics metrics() {
        return metricsCollector.metrics();
    }
}
