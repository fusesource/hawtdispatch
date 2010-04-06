/**
 * Copyright (c) 2008-2009 Apple Inc. All rights reserved.
 * Copyright (C) 2009-2010, Progress Software Corporation and/or its
 * subsidiaries or affiliates.  All rights reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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
 * Dispatch queues are lightweight objects to which runnable objects
 * may be submitted for asynchronous execution.
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
         * runnable objects in a conncurrently.
         */
        GLOBAL_QUEUE,

        /**
         * A serial dispatch queues executes runnable objects
         * submitted to them serially in FIFO order. A
         * queue will only invoke one runnable at a time,
         * ut independent queues may each invoke their blocks
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
     * Creates a new serial dispatch queue with this queue set as it's
     * target queue. See {@link Dispatch#createQueue(String)} for
     * more information about serial dispatch queues.
     *
     * @param label the label to assign the dispatch queue, can be null
     * @return the newly created dispatch queue
     */
    public DispatchQueue createSerialQueue(String label);

    /**
     * Submits a block for asynchronous execution on a dispatch queue.
     * <br/>
     * {@link #dispatchAsync(Runnable)} is the fundamental mechanism for submitting
     * runnable objects to a dispatch queue.
     * <br/>
     * Calls to {@link #dispatchAsync(Runnable)} always return immediately after the runnable has
     * been submitted, and never wait for the runnable to be executed.
     * <br/>
     * The target queue determines whether the block will be invoked serially or
     * concurrently with respect to other blocks submitted to that same queue.
     * Serial queues are processed concurrently with with respect to each other.
     * <br/>
     * The system will retain this queue until the runnable has finished.
     *
     * @param runnable
     * The runnable to submit to the dispatch queue.
     */
    public void dispatchAsync(Runnable runnable);

    /**
     * Submits a runnable for synchronous execution on a dispatch queue.
     * <br/>
     * Submits a runnable to a dispatch queue like dispatch_async(), however
     * {@link #dispatchSync(Runnable)} will not return until the runnable
     * has finished.
     * <br/>

     * Calls to {@link #dispatchSync(Runnable)} targeting the current queue will result
     * in dead-lock. Use of {@link #dispatchSync(Runnable)} is also subject to the same
     * multi-party dead-lock problems that may result from the use of a mutex.
     * Use of {@link #dispatchAsync(Runnable)} is preferred.
     *
     * @param runnable
     * The runnable to be invoked on the target dispatch queue.
     */
    public void dispatchSync(Runnable runnable) throws InterruptedException;

    /**
     * Schedule a runnable for execution on a given queue at a specified time.
     * <br/>
     * @param delay
     * the amount of time to delay before executing the runnable
     * @param unit the unit of time that the delay value is specified in
     * @param runnable
     */
    public void dispatchAfter(long delay, TimeUnit unit, Runnable runnable);

    /**
     * Submits a runnable to a dispatch queue for multiple invocations.
     * <br/>
     * This function
     * waits for the task runnable to complete before returning. If the target queue
     * is a concurrent queue returned by {@link Dispatch#getGlobalQueue()}, the runnable
     * may be invoked concurrently, and it must therefore be reentrant safe.
     * <br/>
     * @param iterations
     * The number of iterations to perform.
     * <br/>
     * @param runnable
     * The runnable to be invoked the specified number of iterations.
     */
    public void dispatchApply(int iterations, Runnable runnable) throws InterruptedException;

    /**
     * Returns the label of the queue that was specified when the
     * queue was created.
     *
     * @return the label of the queue. The result may be null.
     */
    public String getLabel();
                          
}
