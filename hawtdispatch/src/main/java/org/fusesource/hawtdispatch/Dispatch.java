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

import org.fusesource.hawtdispatch.internal.Dispatcher;
import org.fusesource.hawtdispatch.internal.DispatcherConfig;

import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;

/**
 * Provides easy access to a system wide Dispatcher.
 * 
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public class Dispatch {

    final public static Dispatcher DISPATCHER = create();

    private static Dispatcher create() {
        return new Dispatcher() {

            final Dispatcher next = new DispatcherConfig().createDispatcher();

            public DispatchQueue getRandomThreadQueue() {
                return next.getRandomThreadQueue();
            }

            public DispatchQueue getRandomThreadQueue(DispatchPriority priority) {
                return next.getRandomThreadQueue(priority);
            }

            public DispatchQueue getGlobalQueue() {
                return next.getGlobalQueue();
            }

            public DispatchQueue getGlobalQueue(DispatchPriority priority) {
                return next.getGlobalQueue(priority);
            }

            public DispatchQueue createQueue(String label) {
                return next.createQueue(label);
            }

            public DispatchQueue getMainQueue() {
                return next.getMainQueue();
            }

            public void dispatchMain() {
                next.dispatchMain();
            }

            public DispatchQueue getCurrentQueue() {
                return next.getCurrentQueue();
            }

            public DispatchQueue getCurrentThreadQueue() {
                return next.getCurrentThreadQueue();
            }

            public DispatchSource createSource(SelectableChannel channel, int interestOps, DispatchQueue queue) {
                return next.createSource(channel, interestOps, queue);
            }

            public <Event, MergedEvent> CustomDispatchSource<Event, MergedEvent> createSource(EventAggregator<Event, MergedEvent> aggregator, DispatchQueue queue) {
                return next.createSource(aggregator, queue);
            }

            public void addReleaseWatcher(Runnable onRelease) {
            }

            public void retain() {
            }

            public void release() {
            }

            public boolean isReleased() {
                return false;
            }

        };
    }

    /**
     * Returns the default queue that is bound to the main thread.
     * <br/>
     * In order to invoke runnables submitted to the main queue, the application must
     * call {@link #dispatchMain()}}.
     * <br/>
     * @return the main queue.
     */
    public static DispatchQueue getMainQueue() {
        return DISPATCHER.getMainQueue();
    }

    /**
     * Returns the global concurrent queue of default priority.
     *
     * @see #getGlobalQueue(DispatchPriority)  
     * @return the default priority global queue.
     */
    public static DispatchQueue getGlobalQueue() {
        return DISPATCHER.getGlobalQueue();
    }

    /**
     * Returns a well-known global concurrent queue of a given priority level.
     * <br/>
     * The well-known global concurrent queues may not be modified. Calls to
     * {@link Suspendable#suspend()}, {@link Suspendable#resume()}, etc., will
     * have no effect when used with queues returned by this function.
     *
     * @param priority
     * A priority defined in dispatch_queue_priority_t

     * @return the requested global queue.
     */
    public static DispatchQueue getGlobalQueue(DispatchPriority priority) {
        return DISPATCHER.getGlobalQueue(priority);
    }

    /**
     * Creates a new serial dispatch queue to which runnable objects may be submitted.
     * <br/>
     * Serial dispatch queues invoke blocks submitted to them serially in FIFO order. A
     * queue will only invoke one runnable at a time, but independent queues may each
     * invoke their blocks concurrently with respect to each other.
     * <br/>
     * Conceptually a dispatch queue may have its own thread of execution, and
     * interaction between queues is highly asynchronous.
     * <br/>
     * When the dispatch queue is no longer needed, it should be released.
     * Dispatch queues are reference counted via calls to {@link Retained#retain()} and
     * {@link Retained#release()}. Pending runnables submitted to a queue also hold a
     * reference to the queue until they have finished. Once all references to a
     * queue have been released, the queue will be disposed.
     *
     * @param label the label to assign the dispatch queue, can be null
     * @return the newly created dispatch queue
     */
    public static DispatchQueue createQueue(String label) {
        return DISPATCHER.createQueue(label);
    }

    /**
     * Execute blocks submitted to the main queue.
     * <br/>
     * This function "parks" the main thread and waits for blocks to be submitted
     * to the main queue. This function never returns.
     */
    public static void dispatchMain() {
        DISPATCHER.dispatchMain();
    }

    /**
     * Returns the queue on which the currently executing runnable is running.
     * <br/>
     * When {@link #getCurrentQueue()} is called outside of the context of a
     * submitted runnable, it will return the default concurrent queue.
     *
     * @return the queue on which the currently executing runnable is running.
     */
    public static DispatchQueue getCurrentQueue() {
        return DISPATCHER.getCurrentQueue();
    }

    /**
     * Creates a new {@link DispatchSource} to monitor {@link SelectableChannel} objects and
     * automatically submit a handler block to a dispatch queue in response to events.
     *
     * You are allowed to create multiple dispatch sources to the same {@link SelectableChannel}
     * object.
     *
     * @param channel the channel to monitor.
     * @param interestOps A mask of interest ops ({@link SelectionKey#OP_ACCEPT},
     *        {@link SelectionKey#OP_CONNECT}, {@link SelectionKey#OP_READ}, or
     *        {@link SelectionKey#OP_WRITE}) specifying which events are desired.
     * @param queue The dispatch queue to which the event handler tasks will be submited.
     *
     * @return the newly created DispatchSource
     */
    public static DispatchSource createSource(SelectableChannel channel, int interestOps, DispatchQueue queue) {
        return DISPATCHER.createSource(channel, interestOps, queue);
    }

    /**
     * Creates a new {@link CustomDispatchSource} to monitor events merged into
     * the dispatch source and automatically submit a handler block to a dispatch queue
     * in response to the events.
     *
     * @param aggregator the data aggregation strategy to use.
     * @param queue The dispatch queue to which the event handler tasks will be submited.
     *
     * @return the newly created CustomDispatchSource
     */
    public static <Event, MergedEvent> CustomDispatchSource<Event, MergedEvent> createSource(EventAggregator<Event, MergedEvent> aggregator, DispatchQueue queue) {
        return DISPATCHER.createSource(aggregator, queue);
    }

    public static DispatchQueue getCurrentThreadQueue() {
        return DISPATCHER.getCurrentThreadQueue();
    }

    public static DispatchQueue getRandomThreadQueue() {
        return DISPATCHER.getRandomThreadQueue();
    }
}
