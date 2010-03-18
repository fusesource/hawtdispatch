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

import java.nio.channels.SelectableChannel;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.fusesource.hawtdispatch.DispatchOption;
import org.fusesource.hawtdispatch.DispatchPriority;
import org.fusesource.hawtdispatch.DispatchQueue;
import org.fusesource.hawtdispatch.DispatchSource;
import org.fusesource.hawtdispatch.Dispatcher;
import org.fusesource.hawtdispatch.DispatcherConfig;
import org.fusesource.hawtdispatch.internal.AbstractSerialDispatchQueue;
import org.fusesource.hawtdispatch.internal.BaseSuspendable;
import org.fusesource.hawtdispatch.internal.NioDispatchSource;

import static org.fusesource.hawtdispatch.DispatchPriority.*;




/**
 * Implements a simple dispatch system.
 * 
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
final public class HawtDispatcher extends BaseRetained implements Dispatcher {

    public final static ThreadLocal<HawtDispatchQueue> CURRENT_QUEUE = new ThreadLocal<HawtDispatchQueue>();

    final SerialDispatchQueue mainQueue = new SerialDispatchQueue(this, "main");
    final GlobalDispatchQueue globalQueues[];
    final AtomicLong globalQueuedRunnables = new AtomicLong();
    final Random random = new Random();


    private final String label;
    TimerThread timerThread;

    public HawtDispatcher(DispatcherConfig config) {
        this.label = config.getLabel();
        globalQueues = new GlobalDispatchQueue[3];
        for (int i = 0; i < 3; i++) {
            globalQueues[i] = new GlobalDispatchQueue(this, DispatchPriority.values()[i], config.getThreads());
            for ( WorkerThread thread: globalQueues[i].workers.threads) {
                thread.dispatchQueue = new ThreadDispatchQueue(this, thread, globalQueues[i]);
            }
        }
        for (int i = 0; i < 3; i++) {
            globalQueues[i].workers.start();
        }
        timerThread = new TimerThread(this);
        timerThread.start();
    }

    public DispatchQueue getMainQueue() {
        return mainQueue;
    }

    public DispatchQueue getGlobalQueue() {
        return getGlobalQueue(DEFAULT);
    }

    public DispatchQueue getGlobalQueue(DispatchPriority priority) {
        return globalQueues[priority.ordinal()];
    }

    public DispatchQueue createSerialQueue(String label, DispatchOption... options) {
        AbstractSerialDispatchQueue rc = new SerialDispatchQueue(this, label, options);
        rc.setTargetQueue(getGlobalQueue());
        return rc;
    }

    public void dispatchMain() {
        mainQueue.run();
    }

    public DispatchSource createSource(SelectableChannel channel, int interestOps, DispatchQueue queue) {
        return new NioDispatchSource(this, channel, interestOps, queue);
    }

    @Override
    public void onShutdown() {
        for (int i = 0; i < 3; i++) {
            globalQueues[i].workers.shutdown();
        }
        timerThread.shutdown(null);
    }

    public String getLabel() {
        return label;
    }

    public DispatchQueue getCurrentQueue() {
        return CURRENT_QUEUE.get();
    }

    public DispatchQueue getCurrentThreadQueue() {
        WorkerThread thread = WorkerThread.currentWorkerThread();
        if( thread ==null ) {
            return null;
        }
        return thread.dispatchQueue;
    }

    public DispatchQueue getRandomThreadQueue() {
        return getRandomThreadQueue(DEFAULT);
    }

    public DispatchQueue getRandomThreadQueue(DispatchPriority priority) {
        WorkerThread[] threads = globalQueues[priority.ordinal()].workers.threads;
        int i = random.nextInt(threads.length);
        return threads[i].dispatchQueue;
    }

}
