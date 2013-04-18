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

import org.fusesource.hawtdispatch.*;
import org.fusesource.hawtdispatch.internal.pool.SimplePool;
import org.fusesource.hawtdispatch.internal.util.IntrospectionSupport;

import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

/**
 * 
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
final public class GlobalDispatchQueue implements HawtDispatchQueue {

    public final HawtDispatcher dispatcher;
    volatile String label;
    private final DispatchPriority priority;
    final WorkerPool workers;

    public GlobalDispatchQueue(HawtDispatcher dispatcher, DispatchPriority priority, int threads) {
        this.dispatcher = dispatcher;
        this.priority = priority;
        this.label=priority.toString();
        this.workers = new SimplePool(this, threads, priority);
        dispatcher.track(this);
    }

    public void start() {
        workers.start();
    }

    public void shutdown() {
        workers.shutdown();
    }

    public HawtDispatcher getDispatcher() {
        return dispatcher;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public boolean isExecuting() {
        ThreadDispatchQueue tq = dispatcher.getCurrentThreadQueue();
        if( tq!=null ){
            return tq.globalQueue == this;
        }
        return false;
    }

    public LinkedList<Task> getSourceQueue() {
        ThreadDispatchQueue tq = dispatcher.getCurrentThreadQueue();
        if( tq!=null ){
            return tq.getSourceQueue();
        }
        return null;
    }

    public void assertExecuting() {
        assert isExecuting() : getDispatcher().assertMessage(getLabel());
    }

    @Deprecated
    public void execute(final Runnable runnable) {
        execute(new TaskWrapper(runnable));
    }

    @Deprecated()
    public void executeAfter(long delay, TimeUnit unit, Runnable runnable) {
        this.executeAfter(delay, unit, new TaskWrapper(runnable));
    }

    public void execute(Task task) {
        if( dispatcher.shutdownState.get() > 1 ) {
            throw new ShutdownException();
        }
        workers.execute(task);
    }

    public void executeAfter(long delay, TimeUnit unit, Task task) {
        if( dispatcher.shutdownState.get() > 0 ) {
            throw new ShutdownException();
        }
        dispatcher.timerThread.addRelative(task, this, delay, unit);
    }

    public ThreadDispatchQueue getTargetQueue() {
        return null;
    }

    public DispatchPriority getPriority() {
        return priority;
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

    public DispatchQueue createQueue(String label) {
        DispatchQueue rc = dispatcher.createQueue(label);
        rc.setTargetQueue(this);
        return rc;
    }

    public QueueType getQueueType() {
        return QueueType.GLOBAL_QUEUE;
    }

    DispatchQueue[] getThreadQueues() {
        WorkerThread[] threads = workers.getThreads();
        DispatchQueue []rc = new DispatchQueue[threads.length];
        for(int i=0;i < threads.length; i++){
            rc[i] = threads[i].getDispatchQueue();
        }
        return rc;
    }

    public void profile(boolean profile) {
    }

    public boolean profile() {
        return false;
    }


    public Metrics metrics() {
        return null;
    }

}
