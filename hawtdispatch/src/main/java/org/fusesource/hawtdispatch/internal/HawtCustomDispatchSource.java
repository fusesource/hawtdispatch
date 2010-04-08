/**
 * Copyright (C) 2009, Progress Software Corporation and/or its
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
package org.fusesource.hawtdispatch.internal;

import org.fusesource.hawtdispatch.CustomDispatchSource;
import org.fusesource.hawtdispatch.DispatchQueue;
import org.fusesource.hawtdispatch.EventAggregator;

import java.util.LinkedList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static java.lang.String.format;

/**
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
final public class HawtCustomDispatchSource<Event, MergedEvent> extends AbstractDispatchObject implements CustomDispatchSource<Event, MergedEvent>, Runnable {
    public static final boolean DEBUG = false;

    final AtomicBoolean canceled = new AtomicBoolean();
    private Runnable cancelHandler;
    private Runnable eventHandler;

    private final ThreadLocal<MergedEvent> outboundEvent = new ThreadLocal<MergedEvent>();
    private final ThreadLocal<MergedEvent> firedEvent = new ThreadLocal<MergedEvent>();
    private final EventAggregator<Event, MergedEvent> aggregator;
    private MergedEvent pendingEvent;

    public HawtCustomDispatchSource(HawtDispatcher dispatcher, EventAggregator<Event, MergedEvent> aggregator, DispatchQueue queue) {
        this.aggregator = aggregator;
        this.suspended.incrementAndGet();
        setTargetQueue(queue);
    }

    public MergedEvent getData() {
        return firedEvent.get();
    }

    protected final ConcurrentLinkedQueue<MergedEvent> externalQueue = new ConcurrentLinkedQueue<MergedEvent>();
    private final LinkedList<MergedEvent> localQueue = new LinkedList<MergedEvent>();
    protected final AtomicLong size = new AtomicLong();

    public void merge(Event event) {
        debug("merge called");
        assertRetained();
        WorkerThread thread = WorkerThread.currentWorkerThread();
        if( thread!=null ) {
            MergedEvent previous = outboundEvent.get();
            MergedEvent next = aggregator.mergeEvent(previous, event);
            if( next==null ) {
                debug("merge resulted in cancel");
                outboundEvent.remove();
            } else {
                outboundEvent.set(next);
                if( previous==null ) {
                    debug("first merge, posting deferred fire event");
                    thread.getSourceQueue().add(this);
                } else {
                    debug("there was a previous merge, no need to post deferred fire event");
                }
            }
        } else {
            debug("merge not called from a worker thread.. triggering fire event now");
            fireEvent(aggregator.mergeEvent(null, event));
        }
    }

    public void run() {
        debug("deferred fire event executing");
        fireEvent(outboundEvent.get());
        outboundEvent.remove();
    }

    private void fireEvent(final MergedEvent event) {
        if( event!=null ) {
            targetQueue.execute(new Runnable() {
                public void run() {
                    if( isCanceled() ) {
                        debug("canceled");
                        return;
                    }
                    if( isSuspended() ) {
                        debug("fired.. but suspended");
                        synchronized(HawtCustomDispatchSource.this) {
                            pendingEvent = aggregator.mergeEvents(pendingEvent, event);
                        }
                    } else {
                        MergedEvent e=null;
                        synchronized(HawtCustomDispatchSource.this) {
                            e = pendingEvent;
                            pendingEvent = null;
                        }
                        if( e!=null ) {
                            debug("fired.. mergined with previous pending event..");
                            e = aggregator.mergeEvents(e, event);
                        } else {
                            debug("fired.. no previous pending event..");
                            e = event;
                        }
                        firedEvent.set(e);
                        eventHandler.run();
                        firedEvent.remove();
                        debug("eventHandler done");
                    }
                }
            });
        }
    }

    @Override
    protected void onStartup() {
        if( eventHandler==null ) {
            throw new IllegalArgumentException("eventHandler must be set");
        }
        onResume();
    }

    public void cancel() {
        if( canceled.compareAndSet(false, true) ) {
            targetQueue.execute(new Runnable() {
                public void run() {
                    if( cancelHandler!=null ) {
                        cancelHandler.run();
                    }
                }
            });
        }
    }

    @Override
    protected void onResume() {
        debug("onResume");
        targetQueue.execute(new Runnable() {
            public void run() {
                if( isCanceled() ) {
                    return;
                }
                if( !isSuspended() ) {
                    MergedEvent e=null;
                    synchronized(HawtCustomDispatchSource.this) {
                        e = pendingEvent;
                        pendingEvent = null;
                    }
                    if( e!=null ) {
                        firedEvent.set(e);
                        eventHandler.run();
                        firedEvent.remove();
                    }
                }
            }
        });
    }

    @Override
    protected void dispose() {
        cancel();

    }

    public boolean isCanceled() {
        return canceled.get();
    }

    public void setCancelHandler(Runnable cancelHandler) {
        this.cancelHandler = cancelHandler;
    }

    public void setEventHandler(Runnable eventHandler) {
        this.eventHandler = eventHandler;
    }

    protected void debug(String str, Object... args) {
        if (DEBUG) {
            System.out.println(format("[DEBUG] HawtCustomDispatchSource %0#10x: ", System.identityHashCode(this))+format(str, args));
        }
    }

    protected void debug(Throwable thrown, String str, Object... args) {
        if (DEBUG) {
            if (str != null) {
                debug(str, args);
            }
            if (thrown != null) {
                thrown.printStackTrace();
            }
        }
    }

}
