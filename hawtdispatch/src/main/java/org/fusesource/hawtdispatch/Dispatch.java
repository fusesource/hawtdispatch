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
import java.util.List;

/**
 * <p>
 * Provides access to HawtDispatch.
 * </p><p>
 * HawtDispatch is an abstract model for expressing concurrency via simple but
 * powerful API.
 * </p><p>
 * At the core, HawtDispatch provides serial FIFO queues to which runnables may be
 * submitted. Runnables submitted to these dispatch queues are invoked on a pool
 * of threads fully managed by the system. No guarantee is made regarding
 * which thread a runnable will be invoked on; however, it is guaranteed that only
 * one runnable submitted to the FIFO dispatch queue will be invoked at a time.
 * </p><p>
 * HawtDispatch also provides dispatch sources to handle converting
 * events like NIO Socket readiness events into callbacks to runnables
 * executed on the dispatch queues.
 * </p><p>
 * It is encouraged that end users of this api do a static import of the
 * methods defined in this class.
 * </p>
 * <pre>
 * import static org.fusesource.hawtdispatch.Dispatch.*;
 * </pre>
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public class Dispatch {

    final private static Dispatcher DISPATCHER = new DispatcherConfig().createDispatcher();

    public static final DispatchPriority HIGH    = DispatchPriority.HIGH;
    public static final DispatchPriority DEFAULT = DispatchPriority.DEFAULT;
    public static final DispatchPriority LOW     = DispatchPriority.LOW;

    /**
     * <p>
     * Returns the global concurrent queue of default priority.
     * </p>
     *
     * @see #getGlobalQueue(DispatchPriority)  
     * @return the default priority global queue.
     */
    public static DispatchQueue getGlobalQueue() {
        return DISPATCHER.getGlobalQueue();
    }

    /**
     * <p>
     * Returns a well-known global concurrent queue of a given priority level.
     * </p><p>
     * The well-known global concurrent queues may not be modified. Calls to
     * {@link Suspendable#suspend()}, {@link Suspendable#resume()}, etc., will
     * have no effect when used with queues returned by this function.
     * </p>
     *
     * @param priority
     * A priority defined in dispatch_queue_priority_t
     * @return the requested global queue.
     */
    public static DispatchQueue getGlobalQueue(DispatchPriority priority) {
        return DISPATCHER.getGlobalQueue(priority);
    }

    /**
     * <p>
     * Creates a new serial dispatch queue to which runnable objects may be submitted.
     * </p><p>
     * Serial dispatch queues execute runnables submitted to them serially in FIFO order. A
     * queue will only invoke one runnable at a time, but independent queues may each
     * execute their runnables concurrently with respect to each other.
     * </p><p>
     * Conceptually a dispatch queue may have its own thread of execution, and
     * interaction between queues is highly asynchronous.
     * </p><p>
     * When the dispatch queue is no longer needed, it should be released.
     * Dispatch queues are reference counted via calls to {@link Retained#retain()} and
     * {@link Retained#release()}. Pending runnables submitted to a queue also hold a
     * reference to the queue until they have finished. Once all references to a
     * queue have been released, the queue will be disposed.
     * </p>
     *
     * @param label the label to assign the dispatch queue, can be null
     * @return the newly created dispatch queue
     */
    public static DispatchQueue createQueue(String label) {
        return DISPATCHER.createQueue(label);
    }

    /**
     * <p>
     * Creates a new serial dispatch queue to which runnable objects may be submitted.
     * </p>
     * <p>
     * Same thing as <code>createQueue(null)</code>
     * </p>
     * @see #createQueue(String)
     * @return the newly created dispatch queue
     */
    public static DispatchQueue createQueue() {
        return DISPATCHER.createQueue(null);
    }

    /**
     * <p>
     * Returns the queue on which the currently executing runnable is running.
     * </p><p>
     * When {@link #getCurrentQueue()} is called outside of the context of a
     * submitted runnable, it will return null.
     * </p>
     *
     * @return the queue on which the currently executing runnable is running.
     */
    public static DispatchQueue getCurrentQueue() {
        return DISPATCHER.getCurrentQueue();
    }

    /**
     * <p>
     * Creates a new {@link DispatchSource} to monitor {@link SelectableChannel} objects and
     * automatically submit a handler runnable to a dispatch queue in response to events.
     * </p><p>
     * You are allowed to create multiple dispatch sources to the same {@link SelectableChannel}
     * object.
     * </p>
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
     * <p>
     * Creates a new {@link CustomDispatchSource} to monitor events merged into
     * the dispatch source and automatically submit a handler runnable to a dispatch queue
     * in response to the events.
     * </p>
     * 
     * @param aggregator the data aggregation strategy to use.
     * @param queue The dispatch queue to which the event handler tasks will be submited.
     *
     * @return the newly created CustomDispatchSource
     */
    public static <Event, MergedEvent> CustomDispatchSource<Event, MergedEvent> createSource(EventAggregator<Event, MergedEvent> aggregator, DispatchQueue queue) {
        return DISPATCHER.createSource(aggregator, queue);
    }

    /**
     * <p>
     * Gets a dispatch queue which is associated with the current dispatch thread.  Returns
     * null if not called from the context of a runnable being executed by the dispatch system.
     * </p><p>
     * The method is flagged as deprecated since exposing this kind control to the application might
     * prevent the system from being able to more dynamically control the number of threads used
     * to service concurrent requests.
     * </p>
     *
     * @return the current thread queue
     */
    @Deprecated
    public static DispatchQueue getCurrentThreadQueue() {
        return DISPATCHER.getCurrentThreadQueue();
    }

    /**
     * <p>
     * Gets a random dispatch queue which is associated with the an available thread.
     * </p><p>
     * The method is flagged as deprecated since exposing this kind control to the application might
     * prevent the system from being able to more dynamically control the number of threads used
     * to service concurrent requests.
     * </p>
     *
     * @return a random thread queue
     */
    @Deprecated
    public static DispatchQueue getRandomThreadQueue() {
        return DISPATCHER.getRandomThreadQueue();
    }

    /**
     * <p>
     * Gets a random dispatch queue which is associated with the an available thread.
     * </p><p>
     * The method is flagged as deprecated since exposing this kind control to the application might
     * prevent the system from being able to more dynamically control the number of threads used
     * to service concurrent requests.
     * </p>
     *
     * @return a random thread queue
     */
    @Deprecated
    public static DispatchQueue getThreadQueue(int hash, DispatchPriority priority) {
        return DISPATCHER.getThreadQueue(hash, priority);
    }

// Being able to execute stuff on the main thread is critical for some GUI implementations.  For now
// we will not expose these interfaces until are fully cooked / have good test cases for them.
//
//    /**
//     * <p>
//     * Returns the default queue that is bound to the main thread.
//     * </p><p>
//     * In order to invoke runnables submitted to the main queue, the application must
//     * call {@link #dispatchMain()}}.
//     * </p>
//     *
//     * @return the main queue.
//     */
//    public static DispatchQueue getMainQueue() {
//        return DISPATCHER.getMainQueue();
//    }
//
//    /**
//     * <p>
//     * Execute runnables submitted to the main queue.
//     * </p><p>
//     * This function "parks" the main thread and waits for runnables to be submitted
//     * to the main queue. This function never returns.
//     * </p>
//     */
//    public static void dispatchMain() {
//        DISPATCHER.dispatchMain();
//    }
//

    /**
     * If enabled then it enables profiling on the global
     * queues and any newly created queues.  If not enabled
     * then it disables profiling support on all the currently
     * profiled queues and any queues created in the future.
     *
     * @param enabled
     */
    public static void profile(boolean enabled) {
        DISPATCHER.profile(enabled);
    }

    /**
     * Used to get profiling metrics for all the queues
     * currently being profiled.
     *
     * @return
     */
    public static List<Metrics> metrics() {
        return DISPATCHER.metrics();
    }

    /**
     * A Runnable task that does nothing.
     */
    public static final Runnable NOOP = new Runnable() {
        public void run() {}
    };
}
