package org.fusesource.hawtdispatch;

import java.util.LinkedList;

/**
 * Created by IntelliJ IDEA.
 * User: chirino
 * Date: Apr 13, 2010
 * Time: 5:48:06 AM
 * To change this template use File | Settings | File Templates.
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
    public static <T> EventAggregator<T, LinkedList<T>> linkedList(){ return new EventAggregator<T, LinkedList<T>>() {
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

}
