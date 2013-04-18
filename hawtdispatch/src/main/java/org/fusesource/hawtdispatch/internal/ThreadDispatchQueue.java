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

import java.util.LinkedList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

/**
 * 
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
final public class ThreadDispatchQueue implements HawtDispatchQueue {

    volatile String label;

    final LinkedList<Task> localTasks = new LinkedList<Task>();
    final ConcurrentLinkedQueue<Task> sharedTasks = new ConcurrentLinkedQueue<Task>();
    final WorkerThread thread;
    final GlobalDispatchQueue globalQueue;
    private final LinkedList<Task> sourceQueue= new LinkedList<Task>();

    public ThreadDispatchQueue(GlobalDispatchQueue globalQueue, WorkerThread thread) {
        this.thread = thread;
        this.globalQueue = globalQueue;
        this.label=thread.getName()+" pritority: "+globalQueue.getLabel();
        getDispatcher().track(this);
    }

    public LinkedList<Task> getSourceQueue() {
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
        assert isExecuting() : getDispatcher().assertMessage(getLabel());
    }

    public HawtDispatcher getDispatcher() {
        return globalQueue.dispatcher;
    }

    @Deprecated
    public void execute(Runnable runnable) {
        this.execute(new TaskWrapper(runnable));
    }

    @Deprecated
    public void executeAfter(long delay, TimeUnit unit, Runnable runnable) {
        this.executeAfter(delay, unit, new TaskWrapper(runnable));
    }

    public void execute(Task task) {
        // We don't have to take the synchronization hit
        if( Thread.currentThread()!=thread ) {
            sharedTasks.add(task);
            thread.unpark();
        } else {
            localTasks.add(task);
        }
    }

    public Task poll() {
        Task rc = localTasks.poll();
        if (rc ==null) {
            rc = sharedTasks.poll();
        }
        return rc;
    }

    public void executeAfter(long delay, TimeUnit unit, Task task) {
        getDispatcher().timerThread.addRelative(task, this, delay, unit);
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
    }

    public boolean profile() {
        return false;
    }

    public Metrics metrics() {
        return null;
    }

    public WorkerThread getThread() {
        return thread;
    }
}
