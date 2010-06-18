package org.fusesource.hawtdispatch.internal.pool;

import org.fusesource.hawtdispatch.internal.NioManager;
import org.fusesource.hawtdispatch.internal.ThreadDispatchQueue;
import org.fusesource.hawtdispatch.internal.WorkerThread;

import java.io.IOException;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentLinkedQueue;

import static java.lang.String.format;

/**
 */
public class SimpleThread extends WorkerThread {

    private SimplePool pool;
    private ThreadDispatchQueue threadQueue;
    private final NioManager nioManager;
    private final LinkedList<Runnable> sourceQueue= new LinkedList<Runnable>();
    
    public SimpleThread(SimplePool pool) throws IOException {
        this.pool = pool;
        this.nioManager = new NioManager();
        this.threadQueue = new ThreadDispatchQueue(pool.globalQueue, this);
    }

    @Override
    public LinkedList<Runnable> getSourceQueue() {
        return sourceQueue;
    }

    @Override
    public ThreadDispatchQueue getDispatchQueue() {
        return threadQueue;
    }
    @Override
    public void unpark() {
        debug("unpark");
        nioManager.wakeup();
    }

    @Override
    public NioManager getNioManager() {
        return nioManager;
    }

    @Override
    public void run() {
        debug("run start");
        try {
            ConcurrentLinkedQueue<Runnable> sharedQueue = pool.runnables;
            while(!pool.shutdown) {

                Runnable runnable = threadQueue.poll();
                if( runnable==null ) {
                    runnable = sharedQueue.poll();
                    if( runnable==null ) {
                        runnable = sourceQueue.poll();
                    }
                }

                if( runnable == null ) {
                    pool.park(this);
                } else {
                    runnable.run();
                }
            }
        } finally {
            debug("run end");
        }
    }


    public static final boolean DEBUG = false;
    protected void debug(String str, Object... args) {
        if (DEBUG) {
            System.out.println(format("[DEBUG] SimpleThread %s: %s", getName(), format(str, args)));
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
