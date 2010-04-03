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
/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

package org.fusesource.hawtdispatch.internal.pool;

import org.fusesource.hawtdispatch.internal.NioManager;
import org.fusesource.hawtdispatch.internal.ThreadDispatchQueue;
import org.fusesource.hawtdispatch.internal.WorkerThread;

import java.io.IOException;
import java.util.LinkedList;
import java.util.concurrent.*;

import java.util.Collection;

/**
 * A work stealing worker thread based on the ForkJoinWorkerThread by Doug Lea.
 * 
 * @author Doug Lea
 */
public class StealingThread extends WorkerThread {

    /**
     * Capacity of work-stealing queue array upon initialization.
     * Must be a power of two. Initial size must be at least 2, but is
     * padded to minimize cache effects.
     */
    private static final int INITIAL_QUEUE_CAPACITY = 1 << 13;

    /**
     * Maximum work-stealing queue array size.  Must be less than or
     * equal to 1 << 28 to ensure lack of index wraparound. (This
     * is less than usual bounds, because we need leftshift by 3
     * to be in int range).
     */
    private static final int MAXIMUM_QUEUE_CAPACITY = 1 << 28;

    /**
     * The pool this thread works in. Accessed directly by Work.
     */
    final StealingPool pool;

    /**
     * The work-stealing queue array. Size must be a power of two.
     * Initialized when thread starts, to improve memory locality.
     */
    private Runnable[] queue;

    /**
     * A local thread queue which other threads do not steal from.
     */
    ThreadDispatchQueue dispatchQueue;

    /**
     * Index (mod queue.length) of next queue slot to push to or pop
     * from. It is written only by owner thread, via ordered store.
     * Both sp and base are allowed to wrap around on overflow, but
     * (sp - base) still estimates size.
     */
    private volatile int sp;

    /**
     * Index (mod queue.length) of least valid queue slot, which is
     * always the next position to steal from if nonempty.
     */
    private volatile int base;

    /**
     * Activity status. When true, this worker is considered active.
     * Must be false upon construction. It must be true when executing
     * tasks, and BEFORE stealing a task. It must be false before
     * calling pool.sync.
     */
    private boolean active;

    /**
     * Run state of this worker. Supports simple versions of the usual
     * shutdown/shutdownNow control.
     */
    private volatile int runState;

    /**
     * Seed for random number generator for choosing steal victims.
     * Uses Marsaglia xorshift. Must be nonzero upon initialization.
     */
    private int seed;

    /**
     * Number of steals, transferred to pool when idle
     */
    private int stealCount;

    /**
     * Index of this worker in pool array. Set once by pool before
     * running, and accessed directly by pool during cleanup etc.
     */
    int poolIndex;

    /**
     * The last barrier event waited for. Accessed in pool callback
     * methods, but only by current thread.
     */
    long lastEventCount;

    NioManager ioManager;

    /**
     * The number of nested execution that have occurred on this thread.
     */
    int nestedExecutions;
    
    private final LinkedList<Runnable> sourceQueue= new LinkedList<Runnable>();

    /**
     * Creates a WorkerThread operating in the given pool.
     *
     * @param pool the pool this thread works in
     * @throws NullPointerException if pool is null
     */
    protected StealingThread(StealingPool pool) throws IOException {
        if (pool == null) throw new NullPointerException();
        this.pool = pool;
        this.ioManager = new NioManager();
        // Note: poolIndex is set by pool during construction
        // Remaining initialization is deferred to onStart
    }

    // Public access methods

    /**
     * Returns the pool hosting this thread.
     *
     * @return the pool
     */
    public StealingPool getPool() {
        return pool;
    }

    /**
     * Returns the index number of this thread in its pool.  The
     * returned value ranges from zero to the maximum number of
     * threads (minus one) that have ever been created in the pool.
     * This method may be useful for applications that track status or
     * collect results per-worker rather than per-task.
     *
     * @return the index number
     */
    public int getPoolIndex() {
        return poolIndex;
    }


    // Runstate management

    // Runstate values. Order matters
    private static final int RUNNING     = 0;
    private static final int SHUTDOWN    = 1;
    private static final int TERMINATING = 2;
    private static final int TERMINATED  = 3;

    final boolean isShutdown()    { return runState >= SHUTDOWN;  }
    final boolean isTerminating() { return runState >= TERMINATING;  }
    final boolean isTerminated()  { return runState == TERMINATED; }
    final boolean shutdown()      { return transitionRunStateTo(SHUTDOWN); }
    final boolean shutdownNow()   { return transitionRunStateTo(TERMINATING); }

    /**
     * Transitions to at least the given state.
     *
     * @return {@code true} if not already at least at given state
     */
    private boolean transitionRunStateTo(int state) {
        for (;;) {
            int s = runState;
            if (s >= state)
                return false;
            if (UNSAFE.compareAndSwapInt(this, runStateOffset, s, state))
                return true;
        }
    }

    /**
     * Tries to set status to active; fails on contention.
     */
    private boolean tryActivate() {
        if (!active) {
            if (!pool.tryIncrementActiveCount())
                return false;
            active = true;
        }
        return true;
    }

    /**
     * Tries to set status to inactive; fails on contention.
     */
    private boolean tryInactivate() {
        if (active) {
            if (!pool.tryDecrementActiveCount())
                return false;
            active = false;
        }
        return true;
    }

    /**
     * Computes next value for random victim probe.  Scans don't
     * require a very high quality generator, but also not a crummy
     * one.  Marsaglia xor-shift is cheap and works well.
     */
    private static int xorShift(int r) {
        r ^= (r << 13);
        r ^= (r >>> 17);
        return r ^ (r << 5);
    }

    // Lifecycle methods

    /**
     * This method is required to be public, but should never be
     * called explicitly. It performs the main run loop to execute
     * ForkJoinTasks.
     */
    public void run() {
        Throwable exception = null;
        try {
            onStart();
            pool.sync(this); // await first pool event
            mainLoop();
        } catch (Throwable ex) {
            exception = ex;
        } finally {
            onTermination(exception);
        }
    }

    /**
     * Executes tasks until shut down.
     */
    private void mainLoop() {
        while (!isShutdown()) {
            Runnable t = pollTask();
            if (t != null || (t = pollSubmission()) != null)
                t.run();
            else if (tryInactivate())
                pool.sync(this);
        }
    }

    /**
     * Initializes internal state after construction but before
     * processing any tasks. If you override this method, you must
     * invoke super.onStart() at the beginning of the method.
     * Initialization requires care: Most fields must have legal
     * default values, to ensure that attempted accesses from other
     * threads work correctly even before this thread starts
     * processing tasks.
     */
    protected void onStart() {
        // Allocate while starting to improve chances of thread-local
        // isolation
        queue = new Runnable[INITIAL_QUEUE_CAPACITY];
        // Initial value of seed need not be especially random but
        // should differ across threads and must be nonzero
        int p = poolIndex + 1;
        seed = p + (p << 8) + (p << 16) + (p << 24); // spread bits
    }

    /**
     * Performs cleanup associated with termination of this worker
     * thread.  If you override this method, you must invoke
     * {@code super.onTermination} at the end of the overridden method.
     *
     * @param exception the exception causing this thread to abort due
     * to an unrecoverable error, or {@code null} if completed normally
     */
    protected void onTermination(Throwable exception) {
        // Execute remaining local tasks unless aborting or terminating
        while (exception == null && pool.isProcessingTasks() && base != sp) {
            try {
                Runnable t = popTask();
                if (t != null)
                    t.run();
            } catch (Throwable ex) {
                exception = ex;
            }
        }
        // Cancel other tasks, transition status, notify pool, and
        // propagate exception to uncaught exception handler
        try {
            do {} while (!tryInactivate()); // ensure inactive
            runState = TERMINATED;
            pool.workerTerminated(this);
        } catch (Throwable ex) {        // Shouldn't ever happen
            if (exception == null)      // but if so, at least rethrown
                exception = ex;
        } finally {
            if (exception != null)
                StealingPool.rethrowException(exception);
        }
    }

    // Intrinsics-based support for queue operations.

    private static long slotOffset(int i) {
        return ((long) i << qShift) + qBase;
    }

    /**
     * Adds in store-order the given task at given slot of q to null.
     * Caller must ensure q is non-null and index is in range.
     */
    private static void setSlot(Runnable[] q, int i,
                                Runnable t) {
        UNSAFE.putOrderedObject(q, slotOffset(i), t);
    }

    /**
     * CAS given slot of q to null. Caller must ensure q is non-null
     * and index is in range.
     */
    private static boolean casSlotNull(Runnable[] q, int i,
                                       Runnable t) {
        return UNSAFE.compareAndSwapObject(q, slotOffset(i), t, null);
    }

    /**
     * Sets sp in store-order.
     */
    private void storeSp(int s) {
        UNSAFE.putOrderedInt(this, spOffset, s);
    }

    // Main queue methods

    /**
     * Pushes a task. Called only by current thread.
     *
     * @param t the task. Caller must ensure non-null.
     */
    final void pushTask(Runnable t) {
        Runnable[] q = queue;
        int mask = q.length - 1;
        int s = sp;
        setSlot(q, s & mask, t);
        storeSp(++s);
        if ((s -= base) == 1)
            pool.signalWork();
        else if (s >= mask)
            growQueue();
    }

    /**
     * Tries to take a task from the base of the queue, failing if
     * either empty or contended.
     *
     * @return a task, or null if none or contended
     */
    final Runnable deqTask() {
        Runnable t;
        Runnable[] q;
        int i;
        int b;
        if (sp != (b = base) &&
            (q = queue) != null && // must read q after b
            (t = q[i = (q.length - 1) & b]) != null &&
            casSlotNull(q, i, t)) {
            base = b + 1;
            return t;
        }
        return null;
    }

    /**
     * Tries to take a task from the base of own queue, activating if
     * necessary, failing only if empty. Called only by current thread.
     *
     * @return a task, or null if none
     */
    final Runnable locallyDeqTask() {
        Runnable work = dispatchQueue.poll();
        if( work!=null ) {
            return work;
        }
        int b;
        while (sp != (b = base)) {
            if (tryActivate()) {
                Runnable[] q = queue;
                int i = (q.length - 1) & b;
                Runnable t = q[i];
                if (t != null && casSlotNull(q, i, t)) {
                    base = b + 1;
                    return t;
                }
            }
        }
        return sourceQueue.poll();
    }

    /**
     * Returns a popped task, or null if empty. Ensures active status
     * if non-null. Called only by current thread.
     */
    final Runnable popTask() {
        int s = sp;
        while (s != base) {
            if (tryActivate()) {
                Runnable[] q = queue;
                int mask = q.length - 1;
                int i = (s - 1) & mask;
                Runnable t = q[i];
                if (t == null || !casSlotNull(q, i, t))
                    break;
                storeSp(s - 1);
                return t;
            }
        }
        return null;
    }

    /**
     * Specialized version of popTask to pop only if
     * topmost element is the given task. Called only
     * by current thread while active.
     *
     * @param t the task. Caller must ensure non-null.
     */
    final boolean unpushTask(Runnable t) {
        Runnable[] q = queue;
        int mask = q.length - 1;
        int s = sp - 1;
        if (casSlotNull(q, s & mask, t)) {
            storeSp(s);
            return true;
        }
        return false;
    }

    /**
     * Returns next task or null if empty or contended
     */
    final Runnable peekTask() {
        Runnable[] q = queue;
        if (q == null)
            return null;
        int mask = q.length - 1;
        int i = base;
        return q[i & mask];
    }

    /**
     * Doubles queue array size. Transfers elements by emulating
     * steals (deqs) from old array and placing, oldest first, into
     * new array.
     */
    private void growQueue() {
        Runnable[] oldQ = queue;
        int oldSize = oldQ.length;
        int newSize = oldSize << 1;
        if (newSize > MAXIMUM_QUEUE_CAPACITY)
            throw new RejectedExecutionException("Queue capacity exceeded");
        Runnable[] newQ = queue = new Runnable[newSize];

        int b = base;
        int bf = b + oldSize;
        int oldMask = oldSize - 1;
        int newMask = newSize - 1;
        do {
            int oldIndex = b & oldMask;
            Runnable t = oldQ[oldIndex];
            if (t != null && !casSlotNull(oldQ, oldIndex, t))
                t = null;
            setSlot(newQ, b & newMask, t);
        } while (++b != bf);
        pool.signalWork();
    }

    /**
     * Tries to steal a task from another worker. Starts at a random
     * index of threads array, and probes threads until finding one
     * with non-empty queue or finding that all are empty.  It
     * randomly selects the first n probes. If these are empty, it
     * resorts to a full circular traversal, which is necessary to
     * accurately set active status by caller. Also restarts if pool
     * events occurred since last scan, which forces refresh of
     * threads array, in case barrier was associated with resize.
     *
     * This method must be both fast and quiet -- usually avoiding
     * memory accesses that could disrupt cache sharing etc other than
     * those needed to check for and take tasks. This accounts for,
     * among other things, updating random seed in place without
     * storing it until exit.
     *
     * @return a task, or null if none found
     */
    private Runnable scan() {
        Runnable t = null;
        int r = seed;                    // extract once to keep scan quiet
        StealingThread[] ws;       // refreshed on outer loop
        int mask;                        // must be power 2 minus 1 and > 0
        outer:do {
            if ((ws = pool.threads) != null && (mask = ws.length - 1) > 0) {
                int idx = r;
                int probes = ~mask;      // use random index while negative
                for (;;) {
                    r = xorShift(r);     // update random seed
                    StealingThread v = ws[mask & idx];
                    if (v == null || v.sp == v.base) {
                        if (probes <= mask)
                            idx = (probes++ < 0) ? r : (idx + 1);
                        else
                            break;
                    }
                    else if (!tryActivate() || (t = v.deqTask()) == null)
                        continue outer;  // restart on contention
                    else
                        break outer;
                }
            }
        } while (pool.hasNewSyncEvent(this)); // retry on pool events
        seed = r;
        return t;
    }

    /**
     * Gets and removes a local or stolen task.
     *
     * @return a task, if available
     */
    final Runnable pollTask() {
        Runnable t = locallyDeqTask();
        if (t == null && (t = scan()) != null)
            ++stealCount;
        return t;
    }

    /**
     * Gets a local task.
     *
     * @return a task, if available
     */
    final Runnable pollLocalTask() {
        return locallyDeqTask();
    }

    /**
     * Returns a pool submission, if one exists, activating first.
     *
     * @return a submission, if available
     */
    private Runnable pollSubmission() {
        StealingPool p = pool;
        while (p.hasQueuedSubmissions()) {
            Runnable t;
            if (tryActivate() && (t = p.pollSubmission()) != null)
                return t;
        }
        return null;
    }

    /**
     * Drains tasks to given collection c.
     *
     * @return the number of tasks drained
     */
    final int drainTasksTo(Collection<? super Runnable> c) {
        int n = 0;
        Runnable t;
        while (base != sp && (t = deqTask()) != null) {
            c.add(t);
            ++n;
        }
        return n;
    }

    /**
     * Gets and clears steal count for accumulation by pool.  Called
     * only when known to be idle (in pool.sync and termination).
     */
    final int getAndClearStealCount() {
        int sc = stealCount;
        stealCount = 0;
        return sc;
    }

    /**
     * Returns {@code true} if at least one worker in the given array
     * appears to have at least one queued task.
     *
     * @param ws array of threads
     */
    static boolean hasQueuedTasks(StealingThread[] ws) {
        if (ws != null) {
            int len = ws.length;
            for (int j = 0; j < 2; ++j) { // need two passes for clean sweep
                for (int i = 0; i < len; ++i) {
                    StealingThread w = ws[i];
                    if (w != null && w.sp != w.base)
                        return true;
                }
            }
        }
        return false;
    }

    // Support methods for Work

    /**
     * Returns an estimate of the number of tasks in the queue.
     */
    final int getQueueSize() {
        // suppress momentarily negative values
        return Math.max(0, sp - base);
    }

    /**
     * Returns an estimate of the number of tasks, offset by a
     * function of number of idle threads.
     */
    final int getEstimatedSurplusTaskCount() {
        // The halving approximates weighting idle vs non-idle threads
        return (sp - base) - (pool.getIdleThreadCount() >>> 1);
    }

    /**
     * Runs tasks until {@code pool.isQuiescent()}.
     */
    final void helpQuiescePool() {
        for (;;) {
            Runnable t = pollTask();
            if (t != null)
                t.run();
            else if (tryInactivate() && pool.isQuiescent())
                break;
        }
        do {} while (!tryActivate()); // re-activate on exit
    }

    // Unsafe mechanics

    private static final sun.misc.Unsafe UNSAFE = getUnsafe();
    private static final long spOffset =
        objectFieldOffset("sp", StealingThread.class);
    private static final long runStateOffset =
        objectFieldOffset("runState", StealingThread.class);
    private static final long qBase;
    private static final int qShift;

    static {
        qBase = UNSAFE.arrayBaseOffset(Runnable[].class);
        int s = UNSAFE.arrayIndexScale(Runnable[].class);
        if ((s & (s-1)) != 0)
            throw new Error("data type scale not a power of two");
        qShift = 31 - Integer.numberOfLeadingZeros(s);
    }

    private static long objectFieldOffset(String field, Class<?> klazz) {
        try {
            return UNSAFE.objectFieldOffset(klazz.getDeclaredField(field));
        } catch (NoSuchFieldException e) {
            // Convert Exception to corresponding Error
            NoSuchFieldError error = new NoSuchFieldError(field);
            error.initCause(e);
            throw error;
        }
    }

    /**
     * Returns a sun.misc.Unsafe.  Suitable for use in a 3rd party package.
     * Replace with a simple call to Unsafe.getUnsafe when integrating
     * into a jdk.
     *
     * @return a sun.misc.Unsafe
     */
    private static sun.misc.Unsafe getUnsafe() {
        try {
            return sun.misc.Unsafe.getUnsafe();
        } catch (SecurityException se) {
            try {
                return java.security.AccessController.doPrivileged
                    (new java.security
                     .PrivilegedExceptionAction<sun.misc.Unsafe>() {
                        public sun.misc.Unsafe run() throws Exception {
                            java.lang.reflect.Field f = sun.misc
                                .Unsafe.class.getDeclaredField("theUnsafe");
                            f.setAccessible(true);
                            return (sun.misc.Unsafe) f.get(null);
                        }});
            } catch (java.security.PrivilegedActionException e) {
                throw new RuntimeException("Could not initialize intrinsics",
                                           e.getCause());
            }
        }
    }

    public void park() {
        try {
            ioManager.select(-1);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void unpark() {
        ioManager.wakeup();
    }

    public void setDispatchQueue(ThreadDispatchQueue queue) {
        dispatchQueue = queue;
    }

    public ThreadDispatchQueue getDispatchQueue() {
        return dispatchQueue;
    }

    public NioManager getNioManager() {
        return ioManager;
    }
    
    @Override
    public LinkedList<Runnable> getSourceQueue() {
        return sourceQueue;
    }
}
