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

package org.fusesource.hawtdispatch;

import org.fusesource.hawtdispatch.*;

import java.nio.channels.SelectableChannel;
import java.util.List;

/**
 * <p>
 * The Dispatcher interface is used to get or create dispatch objects such
 * as global queues, thread queues, serial queues, or dispatch sources.
 * </p><p>
 * The dispatch queues are {@link java.util.concurrent.Executor}
 * objects that execute tasks asynchronously on thread pools managed by the
 * Dispatcher.
 *
 * <ul>
 * <li>
 *   <b>Global Queues:</b> The tasks submitted to a concurrent dispatch
 *   queue will execute concurrently on the first available thread of
 *   the thread pool.  The order of execution of the tasks is non
 *   deterministic.
 * </li><li>
 *   <b>Thread Queues:</b> The tasks submitted to a thread dispatch
 *   queue will execute serially (FIFO order) on a single thread of
 *   the thread pool.
 * </li><li>
 *   <b>Serial Queues:</b> The tasks submitted to a serial dispatch
 *   queue will execute serially (FIFO order) on the first available
 *   thread of the thread pool.
 * </li>
 * </p><p>
 * All dispatch queues use a shared fixed size thread pool to execute
 * tasks.  All tasks submitted on a dispatch queue should be non-blocking
 * and wait free.
 * </p><p>
 * Dispatch sources provide a way to trigger execution of a user task on
 * on a user selected dispatch queue in response to NIO or application
 * defined events.
 * </p>
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public interface Dispatcher {

    /**
     * @return the thread level dispatch queues for a given dispatch priority.
     */
    public DispatchQueue[] getThreadQueues(DispatchPriority priority);

    /**
     *
     * @return the current thread queue or null of not executing on a thread queue.
     */
    public DispatchQueue getCurrentThreadQueue();

    /**
     * <p>
     * Returns the global concurrent queue of default priority.
     * </p>
     *
     * @see #getGlobalQueue(DispatchPriority)
     * @return the default priority global queue.
     */
    public DispatchQueue getGlobalQueue();

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
    public DispatchQueue getGlobalQueue(DispatchPriority priority);
    
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
     * </p>
     *
     * @param label the label to assign the dispatch queue, can be null
     * @return the newly created dispatch queue
     */
    public DispatchQueue createQueue(String label);
    
//    public DispatchQueue getMainQueue();
//    public void dispatchMain();
    
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
    public DispatchQueue getCurrentQueue();

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
     * @param interestOps A mask of interest ops ({@link java.nio.channels.SelectionKey#OP_ACCEPT},
     *        {@link java.nio.channels.SelectionKey#OP_CONNECT}, {@link java.nio.channels.SelectionKey#OP_READ}, or
     *        {@link java.nio.channels.SelectionKey#OP_WRITE}) specifying which events are desired.
     * @param queue The dispatch queue to which the event handler tasks will be submited.
     *
     * @return the newly created DispatchSource
     */
    public DispatchSource createSource(SelectableChannel channel, int interestOps, DispatchQueue queue);

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
    public <Event, MergedEvent> CustomDispatchSource<Event, MergedEvent> createSource(EventAggregator<Event, MergedEvent> aggregator, DispatchQueue queue);

    /**
     * If enabled then it enables profiling on the global
     * queues and any newly created queues.  If not enabled
     * then it disables profiling support on all the currently
     * profiled queues and any queues created in the future.
     *
     * @param enabled
     */
    public void profile(boolean enabled);

    /**
     * @return true is profiling is enabled.
     */
    public boolean profile();

    /**
     * Used to get profiling metrics for all the queues
     * currently being profiled.
     *
     * @return
     */
    public List<Metrics> metrics();

    /**
     * Shutdown this dispatcher instance.
     */
    public void shutdown();

    /**
     * Restart this dispatcher instance.
     */
    public void restart();
}
