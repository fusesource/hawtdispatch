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

import java.util.concurrent.TimeUnit;

import org.fusesource.hawtdispatch.DispatchOption;
import org.fusesource.hawtdispatch.DispatchPriority;
import org.fusesource.hawtdispatch.DispatchQueue;
import org.fusesource.hawtdispatch.internal.AbstractSerialDispatchQueue;
import org.fusesource.hawtdispatch.internal.util.IntrospectionSupport;

/**
 * 
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public final class SerialDispatchQueue extends AbstractSerialDispatchQueue implements SimpleQueue {

    private final SimpleDispatcher dispatcher;

    private volatile boolean stickToThreadOnNextDispatch;

    private volatile boolean stickToThreadOnNextDispatchRequest;

    SerialDispatchQueue(SimpleDispatcher dispatcher, String label, DispatchOption... options) {
        super(label, options);
        this.dispatcher = dispatcher;
        if (getOptions().contains(DispatchOption.STICK_TO_DISPATCH_THREAD)) {
            stickToThreadOnNextDispatch = true;
        }
    }

    @Override
    public void setTargetQueue(DispatchQueue targetQueue) {
        assertRetained();
        GlobalDispatchQueue global = ((SimpleQueue) targetQueue).isGlobalDispatchQueue();
        if (getOptions().contains(DispatchOption.STICK_TO_CALLER_THREAD) && global != null) {
            stickToThreadOnNextDispatchRequest = true;
        }
        super.setTargetQueue(targetQueue);
    }

    @Override
    public void dispatchAsync(Runnable runnable) {
        assertRetained();

        if (stickToThreadOnNextDispatchRequest) {
            SimpleQueue current = SimpleDispatcher.CURRENT_QUEUE.get();
            if (current != null) {
                SimpleQueue parent;
                while ((parent = current.getTargetQueue()) != null) {
                    current = parent;
                }
                super.setTargetQueue(current);
                stickToThreadOnNextDispatchRequest = false;
            }
        }

        super.dispatchAsync(runnable);
    }

    public void run() {
        SimpleQueue current = SimpleDispatcher.CURRENT_QUEUE.get();
        SimpleDispatcher.CURRENT_QUEUE.set(this);

        try {
            if (stickToThreadOnNextDispatch) {
                stickToThreadOnNextDispatch = false;
                GlobalDispatchQueue global = current.isGlobalDispatchQueue();
                if (global != null) {
                    setTargetQueue(global.getTargetQueue());
                }
            }

            DispatcherThread thread = DispatcherThread.currentDispatcherThread();
            dispatch(thread.executionCounter);
        } finally {
            SimpleDispatcher.CURRENT_QUEUE.set(current);
        }

    }

    public void dispatchAfter(Runnable runnable, long delay, TimeUnit unit) {
        assertRetained();
        dispatcher.timerThread.addRelative(runnable, this, delay, unit);
    }

    public DispatchPriority getPriority() {
        throw new UnsupportedOperationException();
    }

    public Runnable poll() {
        throw new UnsupportedOperationException();
    }

    public GlobalDispatchQueue isGlobalDispatchQueue() {
        return null;
    }

    public SerialDispatchQueue isSerialDispatchQueue() {
        return this;
    }

    public ThreadDispatchQueue isThreadDispatchQueue() {
        return null;
    }

    public SimpleQueue getTargetQueue() {
        assertRetained();
        return (SimpleQueue) targetQueue;
    }

    @Override
    public String toString() {
        return IntrospectionSupport.toString(this, "label", "size", "suspended", "retained");
    }

    int localEnqueueCounter;
    
    public void pick(GlobalDispatchQueue queue, DispatcherThread thread) {
        if( thread==null || localEnqueueCounter > 500 ) {
            localEnqueueCounter=0;
            queue.enqueueExternal(this);
        } else {
            localEnqueueCounter++;
            thread.currentThreadQueue.localEnqueue(this);
        }        
    }

    public DispatchQueue createSerialQueue(String label, DispatchOption... options) {
        DispatchQueue rc = dispatcher.createSerialQueue(label, options);
        rc.setTargetQueue(this);
        return rc;
    }
}