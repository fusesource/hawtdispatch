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

import jsr166y.LinkedTransferQueue;
import org.fusesource.hawtdispatch.internal.WorkerPool;
import org.fusesource.hawtdispatch.internal.WorkerThread;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A work stealing worker pool on the ForkJoinPool by Doug Lea.
 *
 * @author Doug Lea
 */
final public class StealingPool implements WorkerPool {

    /*
     * See the extended comments interspersed below for design,
     * rationale, and walkthroughs.
     */

    /** Mask for packing and unpacking shorts */
    private static final int  shortMask = 0xffff;

    /** Max pool size -- must be a power of two minus 1 */
    private static final int MAX_THREADS =  0x7FFF;

    /**
     * Generator for assigning sequence numbers as pool names.
     */
    private static final AtomicInteger poolNumberGenerator = new AtomicInteger();

    /**
     * Array holding all worker threads in the pool. Initialized upon
     * first use. Array size must be a power of two.  Updates and
     * replacements are protected by workerLock, but it is always kept
     * in a consistent enough state to be randomly accessed without
     * locking by threads performing work-stealing.
     */
    volatile StealingThread[] threads;

    /**
     * Lock protecting access to threads.
     */
    private final ReentrantLock workerLock;

    /**
     * Condition for awaitTermination.
     */
    private final Condition termination;

    /**
     * Sum of per-thread steal counts, updated only when threads are
     * idle or terminating.
     */
    private final AtomicLong stealCount;

    /**
     * Queue for external submissions.
     */
    private final LinkedTransferQueue<Runnable> submissionQueue;

    /**
     * Head of Treiber stack for barrier sync. See below for explanation.
     */
    private volatile WaitQueueNode syncStack;

    /**
     * The count for event barrier
     */
    private volatile long eventCount;

    /**
     * The desired parallelism level.
     */
    private final int parallelism;

    /**
     * The desired priority level of the threads
     */
    private final int priority;

    /**
     * The name of the pool
     */
    private final String name;

    /**
     * Holds number of total (i.e., created and not yet terminated)
     * and running (i.e., not blocked on joins or other managed sync)
     * threads, packed into one int to ensure consistent snapshot when
     * making decisions about creating and suspending spare
     * threads. Updated only by CAS.  Note: CASes in
     * updateRunningCount and preJoin assume that running active count
     * is in low word, so need to be modified if this changes.
     */
    private volatile int workerCounts;

    private static int totalCountOf(int s)           { return s >>> 16;  }
    private static int runningCountOf(int s)         { return s & shortMask; }
    private static int workerCountsFor(int t, int r) { return (t << 16) + r; }

    /**
     * Adds delta (which may be negative) to running count.  This must
     * be called before (with negative arg) and after (with positive)
     * any managed synchronization (i.e., mainly, joins).
     *
     * @param delta the number to add
     */
    final void updateRunningCount(int delta) {
        int s;
        do {} while (!casWorkerCounts(s = workerCounts, s + delta));
    }

    /**
     * Adds delta (which may be negative) to both total and running
     * count.  This must be called upon creation and termination of
     * worker threads.
     *
     * @param delta the number to add
     */
    private void updateWorkerCount(int delta) {
        int d = delta + (delta << 16); // add to both lo and hi parts
        int s;
        do {} while (!casWorkerCounts(s = workerCounts, s + d));
    }

    /**
     * Lifecycle control. High word contains runState, low word
     * contains the number of threads that are (probably) executing
     * tasks. This value is atomically incremented before a worker
     * gets a task to run, and decremented when worker has no tasks
     * and cannot find any. These two fields are bundled together to
     * support correct termination triggering.  Note: activeCount
     * CAS'es cheat by assuming active count is in low word, so need
     * to be modified if this changes
     */
    private volatile int runControl;

    // RunState values. Order among values matters
    private static final int RUNNING     = 0;
    private static final int SHUTDOWN    = 1;
    private static final int TERMINATING = 2;
    private static final int TERMINATED  = 3;

    private static int runStateOf(int c)             { return c >>> 16; }
    private static int activeCountOf(int c)          { return c & shortMask; }
    private static int runControlFor(int r, int a)   { return (r << 16) + a; }

    /**
     * Tries incrementing active count; fails on contention.
     * Called by threads before/during executing tasks.
     *
     * @return true on success
     */
    final boolean tryIncrementActiveCount() {
        int c = runControl;
        return casRunControl(c, c+1);
    }

    /**
     * Tries decrementing active count; fails on contention.
     * Possibly triggers termination on success.
     * Called by threads when they can't find tasks.
     *
     * @return true on success
     */
    final boolean tryDecrementActiveCount() {
        int c = runControl;
        int nextc = c - 1;
        if (!casRunControl(c, nextc))
            return false;
        if (canTerminateOnShutdown(nextc))
            terminateOnShutdown();
        return true;
    }

    /**
     * Returns {@code true} if argument represents zero active count
     * and nonzero runstate, which is the triggering condition for
     * terminating on shutdown.
     */
    private static boolean canTerminateOnShutdown(int c) {
        // i.e. least bit is nonzero runState bit
        return ((c & -c) >>> 16) != 0;
    }

    /**
     * Transition run state to at least the given state. Return true
     * if not already at least given state.
     */
    private boolean transitionRunStateTo(int state) {
        for (;;) {
            int c = runControl;
            if (runStateOf(c) >= state)
                return false;
            if (casRunControl(c, runControlFor(state, activeCountOf(c))))
                return true;
        }
    }


    // Constructors

    /**
     * Creates a {@code ForkJoinPool} with parallelism equal to {@link
     * java.lang.Runtime#availableProcessors}.
     *
     * @throws SecurityException if a security manager exists and
     *         the caller is not permitted to modify threads
     *         because it does not hold {@link
     *         java.lang.RuntimePermission}{@code ("modifyThread")}
     */
    public StealingPool() {
        this("WorkerPool-" + poolNumberGenerator.incrementAndGet(), Runtime.getRuntime().availableProcessors(), Thread.NORM_PRIORITY);
    }


    /**
     * Creates a {@code ForkJoinPool} with the given parallelism .
     *
     * @param parallelism the parallelism level
     * @throws IllegalArgumentException if parallelism less than or
     *         equal to zero, or greater than implementation limit
     */
    public StealingPool(String name, int parallelism, int priority) {
        if (parallelism <= 0 || parallelism > MAX_THREADS)
            throw new IllegalArgumentException();
        this.name = name;
        this.parallelism = parallelism;
        this.priority=priority;
        this.workerLock = new ReentrantLock();
        this.termination = workerLock.newCondition();
        this.stealCount = new AtomicLong();
        this.submissionQueue = new LinkedTransferQueue<Runnable>();


        threads = new StealingThread[parallelism];
        for (int i = 0; i < parallelism; ++i) {
            threads[i] = createWorker(i);
        }

    }

    /**
     *
     * @param index the index to assign worker
     * @return new worker, or null if failed
     */
    private StealingThread createWorker(int index) {
        StealingThread w;
        try {
            w = new StealingThread(this);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        w.poolIndex = index;
        w.setDaemon(true);
        w.setPriority(priority);
        w.setName(name + "-worker-" + index);
        return w;
    }

    /**
     * Returns a good size for worker array given pool size.
     * Currently requires size to be a power of two.
     */
    private static int arraySizeFor(int poolSize) {
        if (poolSize <= 1)
            return 1;
        // See Hackers Delight, sec 3.2
        int c = poolSize >= MAX_THREADS ? MAX_THREADS : (poolSize - 1);
        c |= c >>>  1;
        c |= c >>>  2;
        c |= c >>>  4;
        c |= c >>>  8;
        c |= c >>> 16;
        return c + 1;
    }

    /**
     * Creates or resizes array if necessary to hold newLength.
     * Call only under exclusion.
     *
     * @return the array
     */
    private StealingThread[] ensureWorkerArrayCapacity(int newLength) {
        StealingThread[] ws = threads;
        if (ws == null)
            return threads = new StealingThread[arraySizeFor(newLength)];
        else if (newLength > ws.length)
            return threads = Arrays.copyOf(ws, arraySizeFor(newLength));
        else
            return ws;
    }

    /**
     * Tries to shrink threads into smaller array after one or more terminate.
     */
    private void tryShrinkWorkerArray() {
        StealingThread[] ws = threads;
        if (ws != null) {
            int len = ws.length;
            int last = len - 1;
            while (last >= 0 && ws[last] == null)
                --last;
            int newLength = arraySizeFor(last+1);
            if (newLength < len)
                threads = Arrays.copyOf(ws, newLength);
        }
    }

    /**
     * start the threads
     */
    public final void start() {
        for (int i = 0; i < threads.length; ++i) {
            threads[i].start();
        }
    }

    // Execution methods

    /**
     * Common code for execute, invoke and submit
     */
    private void doSubmit(Runnable task) {
        if (task == null)
            throw new NullPointerException();
        if (isShutdown())
            throw new RejectedExecutionException();
        submissionQueue.offer(task);
        signalIdleWorkers();
    }

    /**
     * Arranges for (asynchronous) execution of the given task.
     *
     * @param task the task
     * @throws NullPointerException if the task is null
     * @throws RejectedExecutionException if the task cannot be
     *         scheduled for execution
     */
    public void execute(Runnable task) {
        doSubmit(task);
    }

    public WorkerThread[] getThreads() {
        return threads;
    }

    /**
     * Returns the targeted parallelism level of this pool.
     *
     * @return the targeted parallelism level of this pool
     */
    public int getParallelism() {
        return parallelism;
    }

    /**
     * Returns an estimate of the number of worker threads that are
     * not blocked waiting to join tasks or for other managed
     * synchronization.
     *
     * @return the number of worker threads
     */
    public int getRunningThreadCount() {
        return runningCountOf(workerCounts);
    }

    /**
     * Returns an estimate of the number of threads that are currently
     * stealing or executing tasks. This method may overestimate the
     * number of active threads.
     *
     * @return the number of active threads
     */
    public int getActiveThreadCount() {
        return activeCountOf(runControl);
    }

    /**
     * Returns an estimate of the number of threads that are currently
     * idle waiting for tasks. This method may underestimate the
     * number of idle threads.
     *
     * @return the number of idle threads
     */
    final int getIdleThreadCount() {
        int c = runningCountOf(workerCounts) - activeCountOf(runControl);
        return (c <= 0) ? 0 : c;
    }

    /**
     * Returns {@code true} if all worker threads are currently idle.
     * An idle worker is one that cannot obtain a task to execute
     * because none are available to steal from other threads, and
     * there are no pending submissions to the pool. This method is
     * conservative; it might not return {@code true} immediately upon
     * idleness of all threads, but will eventually become true if
     * threads remain inactive.
     *
     * @return {@code true} if all threads are currently idle
     */
    public boolean isQuiescent() {
        return activeCountOf(runControl) == 0;
    }

    /**
     * Returns an estimate of the total number of tasks stolen from
     * one thread's work queue by another. The reported value
     * underestimates the actual total number of steals when the pool
     * is not quiescent. This value may be useful for monitoring and
     * tuning fork/join programs: in general, steal counts should be
     * high enough to keep threads busy, but low enough to avoid
     * overhead and contention across threads.
     *
     * @return the number of steals
     */
    public long getStealCount() {
        return stealCount.get();
    }

    /**
     * Accumulates steal count from a worker.
     * Call only when worker known to be idle.
     */
    private void updateStealCount(StealingThread w) {
        int sc = w.getAndClearStealCount();
        if (sc != 0)
            stealCount.addAndGet(sc);
    }

    /**
     * Returns an estimate of the total number of tasks currently held
     * in queues by worker threads (but not including tasks submitted
     * to the pool that have not begun executing). This value is only
     * an approximation, obtained by iterating across all threads in
     * the pool. This method may be useful for tuning task
     * granularities.
     *
     * @return the number of queued tasks
     */
    public long getQueuedTaskCount() {
        long count = 0;
        StealingThread[] ws = threads;
        if (ws != null) {
            for (int i = 0; i < ws.length; ++i) {
                StealingThread t = ws[i];
                if (t != null)
                    count += t.getQueueSize();
            }
        }
        return count;
    }

    /**
     * Returns an estimate of the number of tasks submitted to this
     * pool that have not yet begun executing.  This method takes time
     * proportional to the number of submissions.
     *
     * @return the number of queued submissions
     */
    public int getQueuedSubmissionCount() {
        return submissionQueue.size();
    }

    /**
     * Returns {@code true} if there are any tasks submitted to this
     * pool that have not yet begun executing.
     *
     * @return {@code true} if there are any queued submissions
     */
    public boolean hasQueuedSubmissions() {
        return !submissionQueue.isEmpty();
    }

    /**
     * Removes and returns the next unexecuted submission if one is
     * available.  This method may be useful in extensions to this
     * class that re-assign work in systems with multiple pools.
     *
     * @return the next submission, or {@code null} if none
     */
    protected Runnable pollSubmission() {
        return submissionQueue.poll();
    }

    /**
     * Removes all available unexecuted submitted and forked tasks
     * from scheduling queues and adds them to the given collection,
     * without altering their execution status. These may include
     * artificially generated or wrapped tasks. This method is
     * designed to be invoked only when the pool is known to be
     * quiescent. Invocations at other times may not remove all
     * tasks. A failure encountered while attempting to add elements
     * to collection {@code c} may result in elements being in
     * neither, either or both collections when the associated
     * exception is thrown.  The behavior of this operation is
     * undefined if the specified collection is modified while the
     * operation is in progress.
     *
     * @param c the collection to transfer elements into
     * @return the number of elements transferred
     */
    protected int drainTasksTo(Collection<? super Runnable> c) {
        int n = submissionQueue.drainTo(c);
        StealingThread[] ws = threads;
        if (ws != null) {
            for (int i = 0; i < ws.length; ++i) {
                StealingThread w = ws[i];
                if (w != null)
                    n += w.drainTasksTo(c);
            }
        }
        return n;
    }

    /**
     * Returns a string identifying this pool, as well as its state,
     * including indications of run state, parallelism level, and
     * worker and task counts.
     *
     * @return a string identifying this pool, as well as its state
     */
    public String toString() {
        int ps = parallelism;
        int wc = workerCounts;
        int rc = runControl;
        long st = getStealCount();
        long qt = getQueuedTaskCount();
        long qs = getQueuedSubmissionCount();
        return super.toString() +
            "[" + runStateToString(runStateOf(rc)) +
            ", parallelism = " + ps +
            ", size = " + totalCountOf(wc) +
            ", active = " + activeCountOf(rc) +
            ", running = " + runningCountOf(wc) +
            ", steals = " + st +
            ", tasks = " + qt +
            ", submissions = " + qs +
            "]";
    }

    private static String runStateToString(int rs) {
        switch (rs) {
        case RUNNING: return "Running";
        case SHUTDOWN: return "Shutting down";
        case TERMINATING: return "Terminating";
        case TERMINATED: return "Terminated";
        default: throw new Error("Unknown run state");
        }
    }

    // lifecycle control

    /**
     * Initiates an orderly shutdown in which previously submitted
     * tasks are executed, but no new tasks will be accepted.
     * Invocation has no additional effect if already shut down.
     * Tasks that are in the process of being submitted concurrently
     * during the course of this method may or may not be rejected.
     *
     */
    public void shutdown() {
        transitionRunStateTo(SHUTDOWN);
        if (canTerminateOnShutdown(runControl)) {
            if (threads == null) { // shutting down before threads created
                final ReentrantLock lock = this.workerLock;
                lock.lock();
                try {
                    if (threads == null) {
                        terminate();
                        transitionRunStateTo(TERMINATED);
                        termination.signalAll();
                    }
                } finally {
                    lock.unlock();
                }
            }
            terminateOnShutdown();
        }
    }

    /**
     * Attempts to cancel and/or stop all tasks, and reject all
     * subsequently submitted tasks.  Tasks that are in the process of
     * being submitted or executed concurrently during the course of
     * this method may or may not be rejected. This method cancels
     * both existing and unexecuted tasks, in order to permit
     * termination in the presence of task dependencies. So the method
     * always returns an empty list (unlike the case for some other
     * Executors).
     *
     * @return an empty list
     */
    public List<java.lang.Runnable> shutdownNow() {
        terminate();
        return Collections.emptyList();
    }

    /**
     * Returns {@code true} if all tasks have completed following shut down.
     *
     * @return {@code true} if all tasks have completed following shut down
     */
    public boolean isTerminated() {
        return runStateOf(runControl) == TERMINATED;
    }

    /**
     * Returns {@code true} if the process of termination has
     * commenced but not yet completed.  This method may be useful for
     * debugging. A return of {@code true} reported a sufficient
     * period after shutdown may indicate that submitted tasks have
     * ignored or suppressed interruption, causing this executor not
     * to properly terminate.
     *
     * @return {@code true} if terminating but not yet terminated
     */
    public boolean isTerminating() {
        return runStateOf(runControl) == TERMINATING;
    }

    /**
     * Returns {@code true} if this pool has been shut down.
     *
     * @return {@code true} if this pool has been shut down
     */
    public boolean isShutdown() {
        return runStateOf(runControl) >= SHUTDOWN;
    }

    /**
     * Returns true if pool is not terminating or terminated.
     * Used internally to suppress execution when terminating.
     */
    final boolean isProcessingTasks() {
        return runStateOf(runControl) < TERMINATING;
    }

    /**
     * Blocks until all tasks have completed execution after a shutdown
     * request, or the timeout occurs, or the current thread is
     * interrupted, whichever happens first.
     *
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout argument
     * @return {@code true} if this executor terminated and
     *         {@code false} if the timeout elapsed before termination
     * @throws InterruptedException if interrupted while waiting
     */
    public boolean awaitTermination(long timeout, TimeUnit unit)
        throws InterruptedException {
        long nanos = unit.toNanos(timeout);
        final ReentrantLock lock = this.workerLock;
        lock.lock();
        try {
            for (;;) {
                if (isTerminated())
                    return true;
                if (nanos <= 0)
                    return false;
                nanos = termination.awaitNanos(nanos);
            }
        } finally {
            lock.unlock();
        }
    }

    // Shutdown and termination support

    /**
     * Callback from terminating worker. Nulls out the corresponding
     * threads slot, and if terminating, tries to terminate; else
     * tries to shrink threads array.
     *
     * @param w the worker
     */
    final void workerTerminated(StealingThread w) {
        updateStealCount(w);
        updateWorkerCount(-1);
        final ReentrantLock lock = this.workerLock;
        lock.lock();
        try {
            StealingThread[] ws = threads;
            if (ws != null) {
                int idx = w.poolIndex;
                if (idx >= 0 && idx < ws.length && ws[idx] == w)
                    ws[idx] = null;
                if (totalCountOf(workerCounts) == 0) {
                    terminate(); // no-op if already terminating
                    transitionRunStateTo(TERMINATED);
                    termination.signalAll();
                }
                // We should only loose works when terminating.
                assert !isProcessingTasks();
            }
        } finally {
            lock.unlock();
        }
        signalIdleWorkers();
    }

    /**
     * Initiates termination.
     */
    private void terminate() {
        if (transitionRunStateTo(TERMINATING)) {
            stopAllWorkers();
            signalIdleWorkers();
            interruptUnterminatedWorkers();
            signalIdleWorkers(); // resignal after interrupt
        }
    }

    /**
     * Possibly terminates when on shutdown state.
     */
    private void terminateOnShutdown() {
        if (!hasQueuedSubmissions() && canTerminateOnShutdown(runControl))
            terminate();
    }


    /**
     * Sets each worker's status to terminating. Requires lock to avoid
     * conflicts with add/remove.
     */
    private void stopAllWorkers() {
        final ReentrantLock lock = this.workerLock;
        lock.lock();
        try {
            StealingThread[] ws = threads;
            if (ws != null) {
                for (int i = 0; i < ws.length; ++i) {
                    StealingThread t = ws[i];
                    if (t != null)
                        t.shutdownNow();
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Interrupts all unterminated threads.  This is not required for
     * sake of internal control, but may help unstick user code during
     * shutdown.
     */
    private void interruptUnterminatedWorkers() {
        final ReentrantLock lock = this.workerLock;
        lock.lock();
        try {
            StealingThread[] ws = threads;
            if (ws != null) {
                for (int i = 0; i < ws.length; ++i) {
                    StealingThread t = ws[i];
                    if (t != null && !t.isTerminated()) {
                        try {
                            t.interrupt();
                        } catch (SecurityException ignore) {
                        }
                    }
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /*
     * Nodes for event barrier to manage idle threads.  Queue nodes
     * are basic Treiber stack nodes, also used for spare stack.
     *
     * The event barrier has an event count and a wait queue (actually
     * a Treiber stack).  Workers are enabled to look for work when
     * the eventCount is incremented. If they fail to find work, they
     * may wait for next count. Upon release, threads help others wake
     * up.
     *
     * Synchronization events occur only in enough contexts to
     * maintain overall liveness:
     *
     *   - Submission of a new task to the pool
     *   - Resizes or other changes to the threads array
     *   - pool termination
     *   - A worker pushing a task on an empty queue
     *
     * The case of pushing a task occurs often enough, and is heavy
     * enough compared to simple stack pushes, to require special
     * handling: Method signalWork returns without advancing count if
     * the queue appears to be empty.  This would ordinarily result in
     * races causing some queued waiters not to be woken up. To avoid
     * this, the first worker enqueued in method sync rescans for
     * tasks after being enqueued, and helps signal if any are
     * found. This works well because the worker has nothing better to
     * do, and so might as well help alleviate the overhead and
     * contention on the threads actually doing work.  Also, since
     * event counts increments on task availability exist to maintain
     * liveness (rather than to force refreshes etc), it is OK for
     * callers to exit early if contending with another signaller.
     */
    static final class WaitQueueNode {
        WaitQueueNode next; // only written before enqueued
        volatile StealingThread thread; // nulled to cancel wait
        final long count; // unused for spare stack

        WaitQueueNode(long c, StealingThread w) {
            count = c;
            thread = w;
        }

        /**
         * Wakes up waiter, also clearing thread field
         */
        void signal() {
            StealingThread t = thread;
            if (t != null) {
                thread = null;
                t.unpark();
            }
        }

    }

    /**
     * Ensures that no thread is waiting for count to advance from the
     * current value of eventCount read on entry to this method, by
     * releasing waiting threads if necessary.
     */
    final void ensureSync() {
        long c = eventCount;
        WaitQueueNode q;
        while ((q = syncStack) != null && q.count < c) {
            if (casBarrierStack(q, null)) {
                do {
                    q.signal();
                } while ((q = q.next) != null);
                break;
            }
        }
    }

    /**
     * Increments event count and releases waiting threads.
     */
    private void signalIdleWorkers() {
        long c;
        do {} while (!casEventCount(c = eventCount, c+1));
        ensureSync();
    }

    /**
     * Signals threads waiting to poll a task. Because method sync
     * rechecks availability, it is OK to only proceed if queue
     * appears to be non-empty, and OK if CAS to increment count
     * fails (since some other thread succeeded).
     */
    final void signalWork() {
        if (syncStack != null) {
            long c = eventCount;
            casEventCount(c, c+1);
            WaitQueueNode q = syncStack;
            if (q != null && q.count <= c) {
                if (casBarrierStack(q, q.next))
                    q.signal();
                else
                    ensureSync(); // awaken all on contention
            }
        }
    }

    /**
     * Possibly blocks until event count advances from last value held
     * by caller, or if excess threads, caller is resumed as spare, or
     * caller or pool is terminating. Updates caller's event on exit.
     *
     * @param w the calling worker thread
     */
    final void sync(StealingThread w) {
        updateStealCount(w); // Transfer w's count while it is idle

        if (!w.isShutdown() && isProcessingTasks()) {
            long prev = w.lastEventCount;
            WaitQueueNode node = null;
            WaitQueueNode h;
            long c;
            while ((c = eventCount) == prev &&
                   ((h = syncStack) == null || h.count == prev)) {
                if (node == null)
                    node = new WaitQueueNode(prev, w);
                if (casBarrierStack(node.next = h, node)) {
                    if (!Thread.interrupted() &&
                        node.thread != null &&
                        eventCount == prev &&
                        (h != null || // cover signalWork race
                         (!StealingThread.hasQueuedTasks(threads) &&
                          eventCount == prev)))
                        w.park();
                    c = eventCount;
                    if (node.thread != null) { // help signal if not unparked
                        node.thread = null;
                        if (c == prev)
                            casEventCount(prev, prev + 1);
                    }
                    break;
                }
            }
            w.lastEventCount = c;
            ensureSync();
        }
    }

    /**
     * Returns {@code true} if a new sync event occurred since last
     * call to sync or this method, if so, updating caller's count.
     */
    final boolean hasNewSyncEvent(StealingThread w) {
        long wc = w.lastEventCount;
        long c = eventCount;
        if (wc != c)
            w.lastEventCount = c;
        ensureSync();
        return wc != c || wc != eventCount;
    }


    // Unsafe mechanics

    static final sun.misc.Unsafe UNSAFE = getUnsafe();
    private static final long eventCountOffset =
        objectFieldOffset("eventCount", StealingPool.class);
    private static final long workerCountsOffset =
        objectFieldOffset("workerCounts", StealingPool.class);
    private static final long runControlOffset =
        objectFieldOffset("runControl", StealingPool.class);
    private static final long syncStackOffset =
        objectFieldOffset("syncStack", StealingPool.class);

    private boolean casEventCount(long cmp, long val) {
        return UNSAFE.compareAndSwapLong(this, eventCountOffset, cmp, val);
    }
    private boolean casWorkerCounts(int cmp, int val) {
        return UNSAFE.compareAndSwapInt(this, workerCountsOffset, cmp, val);
    }
    private boolean casRunControl(int cmp, int val) {
        return UNSAFE.compareAndSwapInt(this, runControlOffset, cmp, val);
    }
    private boolean casBarrierStack(WaitQueueNode cmp, WaitQueueNode val) {
        return UNSAFE.compareAndSwapObject(this, syncStackOffset, cmp, val);
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
     * Workaround for not being able to rethrow unchecked exceptions.
     */
    static void rethrowException(Throwable ex) {
        if (ex != null)
            StealingPool.UNSAFE.throwException(ex);
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
}
