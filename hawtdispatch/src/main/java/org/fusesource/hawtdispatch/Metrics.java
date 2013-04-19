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

/**
 * <p>
 * </p>
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public class Metrics {

    /**
     * How long the metrics gathered
     */
    public long durationNS;

    /**
     * The dispatch queue associated with the metrics collected.
     */
    public DispatchQueue queue;

    /**
     * The number of runnable tasks queued.
     */
    public long enqueued;

    /**
     * The number of runnable tasks that have been removed from the queue
     * and executed.
     */
    public long dequeued;

    /**
     * The longest amount of time at runnable task spent waiting in
     * the queue.
     */
    public long maxWaitTimeNS;

    /**
     * The long amount of time a runnable task spent executing in nanoseconds.
     */
    public long maxRunTimeNS;

    /**
     * The sum of all the time spent executing tasks in nanoseconds.
     */
    public long totalRunTimeNS;

    /**
     * The sum of all the time that tasks spent waiting in the queue in nanoseconds.
     */
    public long totalWaitTimeNS;

    @Override
    public String toString() {
        return String.format("{ label:%s, enqueued:%d, dequeued:%d, max_wait_time:%.2f ms, max_run_time:%.2f ms, total_run_time:%.2f ms, total_wait_time:%.2f ms }",
                queue.getLabel(),
                enqueued,
                dequeued,
                maxWaitTimeNS / 1000000.0f,
                maxRunTimeNS / 1000000.0f,
                totalRunTimeNS / 1000000.0f,
                totalWaitTimeNS / 1000000.0f);
    }
}