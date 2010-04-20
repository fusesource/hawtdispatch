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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.fusesource.hawtdispatch.internal.util.QueueSupport;

/**
 * 
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public class SerialDispatchQueue extends AbstractDispatchObject implements HawtDispatchQueue, Runnable {

    private int MAX_DISPATCH_LOOPS = 1000*5;

    protected final String label;
//    protected final Set<DispatchOption> options;

    protected final AtomicInteger executeCounter = new AtomicInteger();
    protected final AtomicLong size = new AtomicLong();
    protected final AtomicLong externalQueueSize = new AtomicLong();
    protected final ConcurrentLinkedQueue<Runnable> externalQueue = new ConcurrentLinkedQueue<Runnable>();
    private final LinkedList<Runnable> localQueue = new LinkedList<Runnable>();
    private final ThreadLocal<Boolean> executing = new ThreadLocal<Boolean>();

    public SerialDispatchQueue(String label) {
        this.label = label;
    }


    public void dispatchAsync(Runnable runnable) {
        assert runnable != null;
        assertRetained();
        enqueue(runnable);
    }

    private void enqueue(Runnable runnable) {
        long sizeWas = size.getAndIncrement();
        // We can take a shortcut...
        if( executing.get()!=null ) {
            localQueue.add(runnable);
        } else {
            if( sizeWas==0 ) {
                retain();
            }

            long lastSize = externalQueueSize.getAndIncrement();
            externalQueue.add(runnable);
            if( lastSize == 0 && suspended.get()<=0 ) {
                dispatchSelfAsync();
            }
        }
    }

    @Override
    protected void dispose() {
        // Runs the disposer on this queue
        enqueue(new Runnable(){
            public void run() {
                Runnable disposer = SerialDispatchQueue.this.disposer;
                if( disposer!=null ) {
                    disposer.run();
                }
            }
        });
        targetQueue.release();
    }

    public void run() {
        HawtDispatchQueue original = HawtDispatcher.CURRENT_QUEUE.get();
        HawtDispatcher.CURRENT_QUEUE.set(this);
        try {
            dispatch();
        } finally {
            HawtDispatcher.CURRENT_QUEUE.set(original);
        }
    }

    protected void dispatch() {
        executing.set(true);
        while( true ) {
            if( executeCounter.incrementAndGet()==1 ) {
                dispatchLoop();

                // Do additional loops for each thread that could
                // not make it in.  This protects us from exiting
                // the dispatch loop but still just after a new
                // thread was trying to get in.
                if( executeCounter.getAndSet(0)==1 ) {
                    break;
                }
            } else {
                break;
            }
        }
        executing.remove();
    }
    
    private void dispatchLoop() {
        int counter=0;
        try {
            Runnable runnable;
            while( suspended.get() <= 0 && counter < MAX_DISPATCH_LOOPS) {

                if( (runnable = localQueue.poll())!=null ) {
                    counter++;
                    dispatch(runnable);
                    continue;
                }
    
                long lsize = externalQueueSize.get();
                if( lsize>0 ) {
                    while( lsize > 0 ) {
                        runnable = externalQueue.poll();
                        if( runnable!=null ) {
                            localQueue.add(runnable);
                            lsize = externalQueueSize.decrementAndGet();
                        }
                    }
                    continue;
                }
                
                break;
            }

        } finally {
            if( counter>0 ) {
                long lsize = size.addAndGet(-counter);
                assert lsize >= 0;
                if( lsize==0 ) {
                    release();
                } else {
                    dispatchSelfAsync();
                }
            }
        }
    }

    public String getLabel() {
        return label;
    }

    @Override
    protected void onStartup() {
        dispatchSelfAsync();
    }

    @Override
    protected void onResume() {
        dispatchSelfAsync();
    }

    public void execute(Runnable command) {
       assertRetained();
        dispatchAsync(command);
    }

    public QueueType getQueueType() {
        return QueueType.SERIAL_QUEUE;
    }

    protected void dispatchSelfAsync() {
        getTargetQueue().dispatchAsync(this);
    }

    protected void dispatch(Runnable runnable) {
        try {
            runnable.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void dispatchSync(Runnable runnable) throws InterruptedException {
       assertRetained();
       dispatchApply(1, runnable);
    }

    public void dispatchApply(int iterations, Runnable runnable) throws InterruptedException {
       assertRetained();
        QueueSupport.dispatchApply(this, iterations, runnable);
    }

    public void dispatchAfter(long delay, TimeUnit unit, Runnable runnable) {
        getDispatcher().timerThread.addRelative(runnable, this, delay, unit);
    }


    public SerialDispatchQueue createSerialQueue(String label) {
        SerialDispatchQueue rc = getDispatcher().createQueue(label);
        rc.setTargetQueue(this);
        return rc;
    }

    public HawtDispatcher getDispatcher() {
        HawtDispatchQueue target = getTargetQueue();
        if (target ==null ) {
            throw new UnsupportedOperationException();
        }
        return target.getDispatcher();
    }

    public SerialDispatchQueue isSerialDispatchQueue() {
        return this;
    }

    public ThreadDispatchQueue isThreadDispatchQueue() {
        return null;
    }

    public GlobalDispatchQueue isGlobalDispatchQueue() {
        return null;
    }

}
