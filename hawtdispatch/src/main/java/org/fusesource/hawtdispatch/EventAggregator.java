/**
 * Copyright (c) 2008-2009 Apple Inc. All rights reserved.
 * Copyright (C) 2010, Progress Software Corporation and/or its
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

/**
 * <p>
 * The EventAggregator interface is used by the {@link CustomDispatchSource} objects to handle
 * coalescing data before passing it to the application.  Implementations of this class should
 * be stateless to remain thread-safe.  You can also use one of several built in implementations:
 * </p>
 *
 * <ul>
 * <li>{@link #INTEGER_ADD}</li>
 * <li>{@link #INTEGER_OR}</li>
 * <li>{@link #LONG_ADD}</li>
 * <li>{@link #LONG_OR}</li> 
 * </ul>
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public interface EventAggregator<Event, MergedEvent> {

    /**
     * <p>
     * Merge the given event with the previous event values.
     * </p>
     *
     * @param previous may be null
     * @param event the value that should be merged
     * @return a newly merged result
     */
    public MergedEvent mergeEvent(MergedEvent previous, Event event);

    /**
     * <p>
     * Merge the given events with the previous event values.
     * </p>
     *
     * @param previous the value of previous merges
     * @param events the value of more merges
     * @return a newly merged result
     */
    public MergedEvent mergeEvents(MergedEvent previous, MergedEvent events);

    /**
     * An EventAggregator that coalesces integer data obtained via calls to
     * {@link CustomDispatchSource#merge(Object)}. Addition is used to coalesce the data.
     */
    public static final EventAggregator<Integer,Integer> INTEGER_ADD = new EventAggregator<Integer,Integer>() {
        public Integer mergeEvent(Integer previous, Integer event) {
            if( previous == null ) {
                return event;
            }
            return previous + event;
        }

        public Integer mergeEvents(Integer previous, Integer events) {
            return previous + events;
        }
    };

    /**
     * An EventAggregator that coalesces long data obtained via calls to
     * {@link CustomDispatchSource#merge(Object)}. Addition is used to coalesce the data.
     */
    public static final EventAggregator<Long,Long> LONG_ADD = new EventAggregator<Long,Long>() {
        public Long mergeEvent(Long previous, Long event) {
            if( previous == null ) {
                return event;
            }
            return previous + event;
        }

        public Long mergeEvents(Long previous, Long events) {
            return previous + events;
        }
    };

    /**
     * An EventAggregator that coalesces integer data obtained via calls to
     * {@link CustomDispatchSource#merge(Object)}. Bit-wise or is used to coalesce the data.
     */
    public static final EventAggregator<Integer,Integer> INTEGER_OR = new EventAggregator<Integer,Integer>() {
        public Integer mergeEvent(Integer previous, Integer event) {
            if( previous == null ) {
                return event;
            }
            return previous + event;
        }

        public Integer mergeEvents(Integer previous, Integer events) {
            return previous + events;
        }
    };

    /**
     * An EventAggregator that coalesces long data obtained via calls to
     * {@link CustomDispatchSource#merge(Object)}. Bit-wise or is used to coalesce the data.
     */
    public static final EventAggregator<Long,Long> LONG_OR = new EventAggregator<Long,Long>() {
        public Long mergeEvent(Long previous, Long event) {
            if( previous == null ) {
                return event;
            }
            return previous | event;
        }

        public Long mergeEvents(Long previous, Long events) {
            return previous | events;
        }
    };
}
