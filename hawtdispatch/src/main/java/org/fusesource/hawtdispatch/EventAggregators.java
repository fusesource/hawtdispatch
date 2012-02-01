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

import java.util.HashSet;
import java.util.LinkedList;

/**
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public class EventAggregators {

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
            return previous | event;
        }

        public Integer mergeEvents(Integer previous, Integer events) {
            return previous | events;
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


    /**
     * An EventAggregator that coalesces object data obtained via calls to
     * {@link CustomDispatchSource#merge(Object)} into a linked list.
     */
    public static <T> EventAggregator<T, LinkedList<T>> linkedList(){
        return new OrderedEventAggregator<T, LinkedList<T>>() {
            public LinkedList<T> mergeEvent(LinkedList<T> previous, T event) {
                if( previous == null ) {
                    previous = new LinkedList<T>();
                }
                previous.add(event);
                return previous;
            }

            public LinkedList<T> mergeEvents(LinkedList<T> previous, LinkedList<T> events) {
                previous.addAll(events);
                return previous;
            }
        };
    }

    /**
     * An EventAggregator that coalesces object data obtained via calls to
     * {@link CustomDispatchSource#merge(Object)} into a hash set.
     */
    public static <T> EventAggregator<T, HashSet<T>> hashSet(){
        return new EventAggregator<T, HashSet<T>>() {
            public HashSet<T> mergeEvent(HashSet<T> previous, T event) {
                if( previous == null ) {
                    previous = new HashSet<T>();
}
                previous.add(event);
                return previous;
            }

            public HashSet<T> mergeEvents(HashSet<T> previous, HashSet<T> events) {
                previous.addAll(events);
                return previous;
            }

            public boolean ordered() {
                return false;
            }
        };
    }
}
