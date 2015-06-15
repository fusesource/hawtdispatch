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

package org.fusesource.hawtdispatch.internal.pool;

import org.fusesource.hawtdispatch.DispatchPriority;
import org.fusesource.hawtdispatch.Task;
import org.fusesource.hawtdispatch.internal.*;

import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;

import static java.lang.String.format;

/**
 */
public class SimplePool implements WorkerPool {

    final ConcurrentLinkedQueue<Task> tasks = new ConcurrentLinkedQueue<Task>();

    final GlobalDispatchQueue globalQueue;
    final String name;
    final int priority;
    final SimpleThread[] threads;
    volatile boolean shutdown = false;
    final ThreadGroup group;

    public SimplePool(GlobalDispatchQueue globalQueue, int parallelism, DispatchPriority priority) {
        this.globalQueue = globalQueue;
        this.name = globalQueue.dispatcher.getLabel()+"-"+priority;
        this.group = new HawtThreadGroup(globalQueue.dispatcher, name);
        this.priority = priority(priority);
        this.threads = new SimpleThread[parallelism];
    }

    static private int priority(DispatchPriority priority) {
        switch(priority) {
            case HIGH:
                return Thread.MAX_PRIORITY;
            case DEFAULT:
                return Thread.NORM_PRIORITY;
            case LOW:
                return Thread.MIN_PRIORITY;
        }
        return 0;
    }

    public void start() {
        shutdown = false;
        for (int i=0; i < threads.length; i++) {
            threads[i] = createWorker(i);
            threads[i].start();
        }
    }

    private SimpleThread createWorker(int index) {
        SimpleThread w;
        try {
            w = new SimpleThread(this);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        w.setDaemon(true);
        w.setPriority(priority);
        w.setName(name + "-" + (index+1));
        return w;
    }

    public WorkerThread[] getThreads() {
        return threads;
    }


    public void shutdown() {
        try {
            // wait for the queue to get drained..
            while( !tasks.isEmpty() ) {
                Thread.sleep(50);
            }

            // now shutdown the threads.
            shutdown = true;
            for (int i=0; i < threads.length; i++) {
                threads[i].unpark();
            }
            for (int i=0; i < threads.length; i++) {
                threads[i].join();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void execute(Task runnable) {
        WorkerThread current = WorkerThread.currentWorkerThread();
        tasks.add(runnable);

        // If there are idle threads.. wake one up to process the runnable.
        for (int i=0; i < threads.length; i++) {

            // no need to wakeup the current thread.
            if( threads[i]==current ) {
                continue;
            }

            // A sleeping thread will be waiting in his selector..
            NioManager nio = threads[i].getNioManager();
            if( nio.wakeupIfSelecting() ) {
                break;
            }
        }
    }

    public void park(SimpleThread thread) {
        try {
           debug("parking thread: %s", thread.getName());
           thread.getNioManager().select(-1);
           debug("unparking thread: %s", thread.getName());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static final boolean DEBUG = false;
    protected void debug(String str, Object... args) {
        if (DEBUG) {
            System.out.println(format("[DEBUG] SimplePool %0#10x: ", System.identityHashCode(this))+format(str, args));
        }
    }
    protected void debug(Throwable thrown, String str, Object... args) {
        if (DEBUG) {
            if (str != null) {
                debug(str, args);
            }
            if (thrown != null) {
                thrown.printStackTrace();
            }
        }
    }

}
