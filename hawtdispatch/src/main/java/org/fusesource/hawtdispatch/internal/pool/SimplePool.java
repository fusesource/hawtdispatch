package org.fusesource.hawtdispatch.internal.pool;

import jsr166y.TransferQueue;
import org.fusesource.hawtdispatch.DispatchPriority;
import org.fusesource.hawtdispatch.internal.GlobalDispatchQueue;
import org.fusesource.hawtdispatch.internal.NioManager;
import org.fusesource.hawtdispatch.internal.WorkerPool;
import org.fusesource.hawtdispatch.internal.WorkerThread;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.String.format;

/**
 */
public class SimplePool implements WorkerPool {

    final ConcurrentLinkedQueue<Runnable> runnables = new ConcurrentLinkedQueue<Runnable>();

    final GlobalDispatchQueue globalQueue;
    final String name;
    final int priority;
    final SimpleThread[] threads;
    volatile boolean shutdown = false;

    public SimplePool(GlobalDispatchQueue globalQueue, int parallelism, DispatchPriority priority) {
        this.globalQueue = globalQueue;
        this.name = globalQueue.dispatcher.getLabel()+"-"+priority;
        this.priority = priority(priority);
        this.threads = new SimpleThread[parallelism];
        for (int i=0; i < parallelism; i++) {
            threads[i] = createWorker(i);
        }
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
        for (int i=0; i < threads.length; i++) {
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


    public void shutdown(){
        shutdown = true;
        try {
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

    public void execute(Runnable runnable) {
        WorkerThread current = WorkerThread.currentWorkerThread();
        runnables.add(runnable);

        // If there are idle threads.. wake one up to process the runnable.
        for (int i=0; i < threads.length; i++) {

            // no need to wakeup the current thread.
            if( threads[i]==current ) {
                continue;
            }

            // A sleeping thread will be waiting in his selector..
            NioManager nio = threads[i].getNioManager();
            if( nio.isSelecting() ) {
                nio.wakeup();
                break;
            }
        }
    }

    public void park(SimpleThread thread) {
        try {
           debug("parking thread: "+thread.getName());
           thread.getNioManager().select(-1);
           debug("unparking thread: "+thread.getName());
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
