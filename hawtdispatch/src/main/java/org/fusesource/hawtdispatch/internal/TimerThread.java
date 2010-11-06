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

import org.fusesource.hawtdispatch.DispatchQueue;
import org.fusesource.hawtdispatch.internal.util.TimerHeap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.fusesource.hawtdispatch.internal.TimerThread.Type.*;


/**
 * 
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
final public class TimerThread extends Thread {
    enum Type {
        RELATIVE,
        ABSOLUTE,
        SHUTDOWN
    }
    final private static class TimerRequest {
        Type type;
        long time;
        TimeUnit unit;
        Runnable runnable;
        DispatchQueue target;
    }

    private final Object mutex = new Object();
    private ArrayList<TimerRequest> requests = new ArrayList<TimerRequest>();
    
    public TimerThread(HawtDispatcher dispatcher) {
        setName(dispatcher.getLabel()+" timer");
        setDaemon(true);
    }

    public final void addAbsolute(Runnable runnable, DispatchQueue target, long time, TimeUnit unit) {
        TimerRequest request = new TimerRequest();
        request.type = ABSOLUTE;
        request.time = time;
        request.unit = unit;
        request.runnable = runnable;
        request.target = target;
        add(request);
    }

    public final void addRelative(Runnable runnable, DispatchQueue target, long delay, TimeUnit unit) {
        TimerRequest request = new TimerRequest();
        request.type = RELATIVE;
        request.time = delay;
        request.unit = unit;
        request.runnable = runnable;
        request.target = target;
        add(request);
    }

    public final void shutdown(Runnable onShutdown) {
        TimerRequest request = new TimerRequest();
        request.type = SHUTDOWN;
        request.runnable = onShutdown;
        add(request);
    }

    private void add(TimerRequest request) {
        request.target.retain();
        synchronized(mutex) {
            requests.add(request);
            mutex.notify();
        }
    }

    public void run() {

        final HashMap<DispatchQueue, LinkedList<Runnable>> readyRequests =
                new HashMap<DispatchQueue, LinkedList<Runnable>>();

        final TimerHeap<TimerRequest> timerHeap = new TimerHeap<TimerRequest>() {
            @Override
            public final void execute(TimerRequest request) {
                LinkedList<Runnable> runnables = readyRequests.get(request.target);
                if( runnables==null ) {
                    runnables = new LinkedList<Runnable>();
                    readyRequests.put(request.target, runnables);
                }
                runnables.add(request.runnable);
            }
        };
        
        ArrayList<TimerRequest> swapped = new ArrayList<TimerRequest>();
        
        try {
            for(;;) {

                synchronized(mutex) {
                    // Swap the arrays.
                    ArrayList<TimerRequest> t = requests;
                    requests = swapped;
                    swapped = t;
                }
                
                if( !swapped.isEmpty() ) {
                    for (TimerRequest request : swapped) {
                        switch( request.type ) {
                        case RELATIVE:
                            timerHeap.addRelative(request, request.time, request.unit);
                            break;
                        case ABSOLUTE:
                            timerHeap.addAbsolute(request, request.time, request.unit);
                            break;
                        case SHUTDOWN:
                            if( request.runnable!=null ) {
                                timerHeap.execute(request);
                            }
                            return;
                        }
                    }
                    swapped.clear();
                }
                
                timerHeap.executeReadyTimers();

                if( !readyRequests.isEmpty() ) {
                    for (Map.Entry<DispatchQueue,LinkedList<Runnable>> entry: readyRequests.entrySet()) {
                        final DispatchQueue queue = entry.getKey();
                        final LinkedList<Runnable> runnables = entry.getValue();
                        if( runnables.size() > 1 ) {
                            // execute the runnables as a batch.
                            queue.dispatchAsync(new Runnable(){
                                public void run() {
                                    for ( Runnable runnable: runnables) {
                                        runnable.run();
                                    }
                                }
                            });
                            for ( Runnable runnable: runnables) {
                                queue.release();
                            }
                        } else {
                            queue.dispatchAsync(runnables.getFirst());
                            queue.release();
                        }
                    }
                    readyRequests.clear();
                }


                long start = System.nanoTime();
                long next = timerHeap.timeToNext(TimeUnit.NANOSECONDS);
                
                if( next==0 ) {
                    continue;
                }
                
                // if it's coming up soon.. just spin..
                if( next>0 && next < 1000 ) {
                    while( System.nanoTime()-start < next ) {
                    }
                    continue;
                }
                
                long waitms = next / 1000000;
                int waitns = (int) (next % 1000000);
                synchronized(mutex) {
                    if( requests.isEmpty() ) {
                        if(next==-1) {
                            mutex.wait();
                        }  else {
                            mutex.wait(waitms, waitns);
                        }
                    }
                }                
            }
        } catch (InterruptedException e) {
        }
    }
}
