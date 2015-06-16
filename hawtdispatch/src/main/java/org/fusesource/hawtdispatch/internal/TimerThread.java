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

import org.fusesource.hawtdispatch.DispatchQueue;
import org.fusesource.hawtdispatch.Task;
import org.fusesource.hawtdispatch.internal.util.TimerHeap;

import java.util.*;
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
        Task task;
        DispatchQueue target;
    }

    private final Object mutex = new Object();
    private ArrayList<TimerRequest> requests = new ArrayList<TimerRequest>();
    
    public TimerThread(HawtDispatcher dispatcher) {
        setName(dispatcher.getLabel()+" timer");
        setDaemon(true);
    }

    public final void addAbsolute(Task task, DispatchQueue target, long time, TimeUnit unit) {
        TimerRequest request = new TimerRequest();
        request.type = ABSOLUTE;
        request.time = time;
        request.unit = unit;
        request.task = task;
        request.target = target;
        add(request);
    }

    public final void addRelative(Task task, DispatchQueue target, long delay, TimeUnit unit) {
        TimerRequest request = new TimerRequest();
        request.type = RELATIVE;
        request.time = delay;
        request.unit = unit;
        request.task = task;
        request.target = target;
        add(request);
    }

    public final void shutdown(Task onShutdown, DispatchQueue target) {
        TimerRequest request = new TimerRequest();
        request.type = SHUTDOWN;
        request.target = target;
        request.task = onShutdown;
        add(request);
    }

    private void add(TimerRequest request) {
        synchronized(mutex) {
            requests.add(request);
            mutex.notify();
        }
    }

    public void run() {

        final HashMap<DispatchQueue, LinkedList<Task>> readyRequests =
                new HashMap<DispatchQueue, LinkedList<Task>>();

        final TimerHeap<TimerRequest> timerHeap = new TimerHeap<TimerRequest>() {
            @Override
            public final void execute(TimerRequest request) {
                LinkedList<Task> tasks = readyRequests.get(request.target);
                if( tasks==null ) {
                    tasks = new LinkedList<Task>();
                    readyRequests.put(request.target, tasks);
                }
                tasks.add(request.task);
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
                            List<TimerRequest> requests = timerHeap.clear();
                            for (TimerRequest r : requests) {
                                // execute them all..
                                r.target.execute(r.task);
                            }
                            if( request.task !=null ) {
                                request.task.run();
                            }
                            return;
                        }
                    }
                    swapped.clear();
                }
                
                timerHeap.executeReadyTimers();

                if( !readyRequests.isEmpty() ) {
                    for (Map.Entry<DispatchQueue,LinkedList<Task>> entry: readyRequests.entrySet()) {
                        final DispatchQueue queue = entry.getKey();
                        final LinkedList<Task> tasks = entry.getValue();
                        if( tasks.size() > 1 ) {
                            // execute the tasks as a batch.
                            queue.execute(new Task(){
                                public void run() {
                                    for ( Task task: tasks) {
                                        task.run();
                                    }
                                }
                            });
                        } else {
                            queue.execute(tasks.getFirst());
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
