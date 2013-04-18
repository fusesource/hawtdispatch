/**
 * Copyright (c) 2008-2009 Apple Inc. All rights reserved.
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

package org.fusesource.hawtdispatch;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 *
 * <p>
 * Dispatch queues are lightweight objects to which runnable objects
 * may be submitted for asynchronous execution and therefore are
 * {@link Executor} objects.
 * </p>
 *
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public interface DispatchQueue extends DispatchObject, Executor {

    /**
     * Defines the types of dispatch queues supported by the system.
     */
    enum QueueType {

        /**
         * A global queue represents a dispatch queue which executes
         * runnable objects in a concurrently.
         */
        GLOBAL_QUEUE,

        /**
         * A serial dispatch queues executes runnable objects
         * submitted to them serially in FIFO order. A
         * queue will only invoke one runnable at a time,
         * ut independent queues may each invoke their runnables
         * concurrently with respect to each other.
         */
        SERIAL_QUEUE,

        /**
         * A thread queue is a dispatch queue associated with a specific
         * thread.  It executes runnable objects submitted to them
         * serially in FIFO order.   
         */
        THREAD_QUEUE
    }

    /**
     * @return the type of dispatch queue that this object implements.
     */
    public QueueType getQueueType();

    /**
     * <p>
     * Creates a new serial dispatch queue with this queue set as it's
     * target queue. See {@link Dispatch#createQueue(String)} for
     * more information about serial dispatch queues.
     * </p>
     *
     * @param label the label to assign the dispatch queue, can be null
     * @return the newly created dispatch queue
     */
    public DispatchQueue createQueue(String label);

    /**
     * <p>
     * Submits a runnable for asynchronous execution on a dispatch queue.
     * </p><p>
     * {@link #execute(Runnable)} is the fundamental mechanism for submitting
     * runnable objects to a dispatch queue.
     * </p><p>
     * Calls to {@link #execute(Runnable)} always return immediately after the runnable has
     * been submitted, and never wait for the runnable to be executed.
     * </p><p>
     * The target queue determines whether the runnable will be invoked serially or
     * concurrently with respect to other runnables submitted to that same queue.
     * Serial queues are processed concurrently with with respect to each other.
     * </p>
     *
     * @param runnable
     * The runnable to submit to the dispatch queue.
     */
    void execute(Runnable runnable);

    /**
     * <p>
     * Submits a task for asynchronous execution on a dispatch queue.
     * </p><p>
     * {@link #execute(Task)} is the fundamental mechanism for submitting
     * runnable objects to a dispatch queue.
     * </p><p>
     * Calls to {@link #execute(Task)} always return immediately after the runnable has
     * been submitted, and never wait for the runnable to be executed.
     * </p><p>
     * The target queue determines whether the runnable will be invoked serially or
     * concurrently with respect to other runnables submitted to that same queue.
     * Serial queues are processed concurrently with with respect to each other.
     * </p>
     *
     * @param task
     * The task to submit to the dispatch queue.
     */
    void execute(Task task);

    /**
     * <p>
     * Schedule a runnable for execution on a given queue at a specified time.
     * </p>
     *
     * @param delay
     * the amount of time to delay before executing the runnable
     * @param unit the unit of time that the delay value is specified in
     * @param runnable
     */
    public void executeAfter(long delay, TimeUnit unit, Runnable runnable);

    /**
     * <p>
     * Schedule a task for execution on a given queue at a specified time.
     * </p>
     *
     * @param delay
     * the amount of time to delay before executing the runnable
     * @param unit the unit of time that the delay value is specified in
     * @param task
     */
    public void executeAfter(long delay, TimeUnit unit, Task task);

//
//  This is an API method that libdispatch supports, but even they don't recommend it's
//  use.  Due to the static nature of our thread pool implementation it's even more dangerous.
//  so leaving it commented out for now so that folks don't use it.  Perhaps we can enable it
//  in a future release.
//
//    /**
//     * <p>
//     * Submits a runnable for synchronous execution on a dispatch queue.
//     * </p><p>
//     * Submits a runnable to a dispatch queue like dispatch_async(), however
//     * {@link #dispatchSync(Runnable)} will not return until the runnable
//     * has finished.
//     * </p><p>
//     * Calls to {@link #dispatchSync(Runnable)} targeting the current queue will result
//     * in dead-lock. Use of {@link #dispatchSync(Runnable)} is also subject to the same
//     * multi-party dead-lock problems that may result from the use of a mutex.
//     * Use of {@link #execute(Runnable)} is preferred.
//     * </p>
//     *
//     * @param runnable
//     * The runnable to be invoked on the target dispatch queue.
//     */
//    public void dispatchSync(Runnable runnable) throws InterruptedException;

//  Ditto..
//
//    /**
//     * <p>
//     * Submits a runnable to a dispatch queue for multiple invocations.
//     * </p><p>
//     * This function
//     * waits for the task runnable to complete before returning. If the target queue
//     * is a concurrent queue returned by {@link Dispatch#getGlobalQueue()}, the runnable
//     * may be invoked concurrently, and it must therefore be thread safe.
//     * </p>
//     *
//     * @param iterations
//     * The number of iterations to perform.
//     * <br/>
//     * @param runnable
//     * The runnable to be invoked the specified number of iterations.
//     */
//    public void dispatchApply(int iterations, Runnable runnable) throws InterruptedException;

    /**
     * <p>
     * Returns the label of the queue.
     * </p>
     *
     * @return the label of the queue. The result may be null.
     */
    public String getLabel();

    /**
     * <p>
     * Sets the label of the queue.
     * </p>
     *
     * @param label the label of the queue.
     */
    public void setLabel(String label);

    /**
     * <p>
     * Returns true if this dispatch queue is executing the caller.
     * </p>
     *
     * @return if this dispatch queue is executing the caller.
     */
    public boolean isExecuting();

    /**
     * Asserts that the current dispatch queue is executing.
     */
    public void assertExecuting();

    /**
     * Enables or disables profiler metric tracking on the queue.
     * @param on
     */
    void profile(boolean on);

    /**
     * @return true is profiling is enabled.
     */
    boolean profile();

    /**
     * Returns the usage metrics of this queue.  Only returns a value
     * if the queue has profiling enabled.
     *
     * @return new metric counters accumulated since last called or null if the queue has not been used.
     */
    Metrics metrics();

}
