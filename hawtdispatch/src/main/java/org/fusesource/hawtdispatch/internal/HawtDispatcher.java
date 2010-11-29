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

import org.fusesource.hawtdispatch.*;

import java.nio.channels.SelectableChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

import static org.fusesource.hawtdispatch.DispatchPriority.DEFAULT;


/**
 * Implements a simple dispatch system.
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
final public class HawtDispatcher extends BaseRetained implements Dispatcher {

    public final static ThreadLocal<HawtDispatchQueue> CURRENT_QUEUE = new ThreadLocal<HawtDispatchQueue>();

    private final GlobalDispatchQueue DEFAULT_QUEUE;
    private final Object HIGH_MUTEX = new Object();
    private GlobalDispatchQueue HIGH_QUEUE;
    private final Object LOW_MUTEX = new Object();
    private GlobalDispatchQueue LOW_QUEUE;

    private final SerialDispatchQueue mainQueue = new SerialDispatchQueue("main") {
        public HawtDispatcher getDispatcher() {
            return HawtDispatcher.this;
        }
    };

    private final String label;
    final TimerThread timerThread;

    private final int threads;
    private volatile boolean profile;
    final int drains;

    public HawtDispatcher(DispatcherConfig config) {
        this.threads = config.getThreads();
        this.label = config.getLabel();
        this.profile = config.isProfile();
        this.drains = config.getDrains();

        DEFAULT_QUEUE = new GlobalDispatchQueue(this, DispatchPriority.DEFAULT, config.getThreads());
        DEFAULT_QUEUE.start();
        DEFAULT_QUEUE.profile(profile);

        timerThread = new TimerThread(this);
        timerThread.start();
    }

    public DispatchQueue getMainQueue() {
        return mainQueue;
    }

    public DispatchQueue getGlobalQueue() {
        return getGlobalQueue(DEFAULT);
    }

    public GlobalDispatchQueue getGlobalQueue(DispatchPriority priority) {
        switch (priority) {
            case DEFAULT:
                return DEFAULT_QUEUE;
            case HIGH:
                // lazy load the high queue to avoid creating it's thread if the application is not using
                // the queue at all.
                synchronized(HIGH_MUTEX) {
                    if( HIGH_QUEUE==null ) {
                        HIGH_QUEUE = new GlobalDispatchQueue(this, DispatchPriority.HIGH, threads);
                        HIGH_QUEUE.start();
                        HIGH_QUEUE.profile(profile);
                    }
                    return HIGH_QUEUE;
                }
            case LOW:
                // lazy load the low queue to avoid creating it's thread if the application is not using
                // the queue at all.
                synchronized(LOW_MUTEX) {
                    if( LOW_QUEUE==null ) {
                        LOW_QUEUE = new GlobalDispatchQueue(this, DispatchPriority.HIGH, threads);
                        LOW_QUEUE.start();
                        LOW_QUEUE.profile(profile);
                    }
                    return LOW_QUEUE;
                }
        }
        throw new AssertionError("switch missing case");
    }

    public SerialDispatchQueue createQueue(String label) {
        SerialDispatchQueue rc = new SerialDispatchQueue(label);
        rc.setTargetQueue(getGlobalQueue());
        rc.profile(profile);
        return rc;
    }

    public void dispatchMain() {
        mainQueue.run();
    }

    public DispatchSource createSource(SelectableChannel channel, int interestOps, DispatchQueue queue) {
        return new NioDispatchSource(this, channel, interestOps, queue);
    }

    public <Event, MergedEvent> CustomDispatchSource<Event, MergedEvent> createSource(EventAggregator<Event, MergedEvent> aggregator, DispatchQueue queue) {
        return new HawtCustomDispatchSource(this, aggregator, queue);
    }

    @Override
    public void dispose() {
        DEFAULT_QUEUE.shutdown();
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
        return thread.getDispatchQueue();
    }

    public DispatchQueue getRandomThreadQueue() {
        return getRandomThreadQueue(DEFAULT);
    }

    public DispatchQueue getRandomThreadQueue(DispatchPriority priority) {
        return getGlobalQueue(priority).getRandomThreadQueue();
    }
    
    public DispatchQueue getThreadQueue(int hash, DispatchPriority priority) {
        return getGlobalQueue(priority).getThreadQueue(hash);
    }

    final static public WeakHashMap<HawtDispatchQueue, Object> queues = new WeakHashMap<HawtDispatchQueue, Object>();

    void track(HawtDispatchQueue queue) {
        synchronized (queues) {
            queues.put(queue, Boolean.TRUE);
        }
    }

    void untrack(HawtDispatchQueue queue) {
        synchronized (queues) {
            queues.remove(queue);
        }
    }

    public void profile(boolean on) {
        profile = on;
        synchronized (queues) {
            for( HawtDispatchQueue queue : new ArrayList<HawtDispatchQueue>(queues.keySet()) ) {
                if( queue!=null ) {
                    queue.profile(on);
                }
            }
        }
    }

    public List<Metrics> metrics() {
        synchronized (queues) {
            ArrayList<Metrics> rc = new ArrayList<Metrics>();
            for( HawtDispatchQueue queue : queues.keySet() ) {
                if( queue!=null ) {
                    Metrics metrics = queue.metrics();
                    if( metrics!=null ) {
                        rc.add(metrics);
                    }
                }
            }
            return rc;
        }

    }

}
