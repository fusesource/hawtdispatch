/**
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
 */
package org.fusesource.hawtdispatch;

/**
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public interface EventAggregator<Event, MergedEvent> {

    public MergedEvent mergeEvent(MergedEvent previous, Event event);
    public MergedEvent mergeEvents(MergedEvent previous, MergedEvent events);


    public static final EventAggregator<Integer,Integer> INTEGER_ADDER = new EventAggregator<Integer,Integer>() {
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

    public static final EventAggregator<Long,Long> LONG_ADDER = new EventAggregator<Long,Long>() {
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

    public static final EventAggregator<Integer,Integer> INTEGER_ORER = new EventAggregator<Integer,Integer>() {
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

    public static final EventAggregator<Long,Long> LONG_ORER = new EventAggregator<Long,Long>() {
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
