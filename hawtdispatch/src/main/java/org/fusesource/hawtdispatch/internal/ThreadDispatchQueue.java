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

import java.util.Collections;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import org.fusesource.hawtdispatch.DispatchOption;
import org.fusesource.hawtdispatch.DispatchPriority;
import org.fusesource.hawtdispatch.DispatchQueue;
import org.fusesource.hawtdispatch.internal.pool.StealingThread;
import org.fusesource.hawtdispatch.internal.util.QueueSupport;

/**
 * 
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
final public class ThreadDispatchQueue implements HawtDispatchQueue {

    final String label;

    final LinkedList<Runnable> localRunnables = new LinkedList<Runnable>();
    final ConcurrentLinkedQueue<Runnable> sharedRunnables = new ConcurrentLinkedQueue<Runnable>();

    final WorkerThread thread;
    final GlobalDispatchQueue globalQueue;
    private final HawtDispatcher dispatcher;
    
    public ThreadDispatchQueue(HawtDispatcher dispatcher, WorkerThread thread, GlobalDispatchQueue globalQueue) {
        this.dispatcher = dispatcher;
        this.thread = thread;
        this.globalQueue = globalQueue;
        this.label=thread.getName()+" pritority: "+globalQueue.getLabel();
    }

    public String getLabel() {
        return label;
    }

    public HawtDispatcher getDispatcher() {
        return dispatcher;
    }


    public void execute(java.lang.Runnable runnable) {
        dispatchAsync(runnable);
    }
    
    public void dispatchAsync(java.lang.Runnable runnable) {
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

    public void dispatchAfter(java.lang.Runnable runnable, long delay, TimeUnit unit) {
        throw new RuntimeException("TODO: implement me.");
    }

    public void dispatchSync(final java.lang.Runnable runnable) throws InterruptedException {
        dispatchApply(1, runnable);
    }
    
    public void dispatchApply(int iterations, final java.lang.Runnable runnable) throws InterruptedException {
        QueueSupport.dispatchApply(this, iterations, runnable);
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

    public void addReleaseWatcher(java.lang.Runnable finalizer) {
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

    public void release() {
    }

    public void retain() {
    }
    public boolean isReleased() {
        return false;
    }

    public Set<DispatchOption> getOptions() {
        return Collections.emptySet();
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

    public DispatchQueue createSerialQueue(String label, DispatchOption... options) {
        DispatchQueue rc = dispatcher.createSerialQueue(label, options);
        rc.setTargetQueue(this);
        return rc;
    }

    public QueueType getQueueType() {
        return QueueType.THREAD_QUEUE;
    }
}
