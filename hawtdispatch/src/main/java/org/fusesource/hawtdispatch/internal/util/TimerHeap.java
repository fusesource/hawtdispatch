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

package org.fusesource.hawtdispatch.internal.util;

import java.util.*;
import java.util.concurrent.TimeUnit;

public abstract class TimerHeap<V> {
    private final TreeMap<Long, LinkedList<V>> timers = new TreeMap<Long, LinkedList<V>>();
    private final TimeUnit resolution = TimeUnit.NANOSECONDS;
    private int size = 0;

    public final void addAbsolute(V timed, long time, TimeUnit timeUnit) {
        addInternal(timed, System.nanoTime() + resolution.convert(resolution.convert(time, timeUnit), timeUnit));
    }

    public final void addRelative(V timed, long delay, TimeUnit timeUnit) {

        addInternal(timed, System.nanoTime() + resolution.convert(delay, timeUnit));
    }

    private void addInternal(V timed, long eTime) {
        LinkedList<V> list = new LinkedList<V>();
        list.add(timed);

        LinkedList<V> old = timers.put(eTime, list);
        if (old != null) {
            list.addAll(old);
        }
        size++;
    }

    public int size() {
        return size;
    }

    /**
     * Returns the time of the next scheduled event.
     * 
     * @return -1 if there are no events, otherwise the time that the next timer
     *         should fire.
     */
    public final long timeToNext(TimeUnit unit) {
        if (timers.isEmpty()) {
            return -1;
        } else {
            return unit.convert(Math.max(0, timers.firstKey() - System.nanoTime()), resolution);
        }
    }

    /**
     * Executes ready timers.
     */
    public final void executeReadyTimers() {
        LinkedList<V> ready = null;
        if (timers.isEmpty()) {
            return;
        } else {
            long now = System.nanoTime();
            long first = timers.firstKey();
            if (first > now) {
                return;
            }
            ready = new LinkedList<V>();

            while (first <= now) {
                ready.addAll(timers.remove(first));
                if (timers.isEmpty()) {
                    break;
                }
                first = timers.firstKey();

            }
        }

        for (V timed : ready) {
            try {
                execute(timed);
                size--;
            } catch (Throwable thrown) {
                Thread thread = Thread.currentThread();
                thread.getUncaughtExceptionHandler().uncaughtException(thread, thrown);
            }
        }
    }

    public List<V> clear() {
        ArrayList<V> rc = new ArrayList<V>(size());
        for (LinkedList<V> t : timers.values()) {
            rc.addAll(t);
        }
        timers.clear();
        return rc;
    }

    /**
     * Subclass must override this to execute ready timers
     * 
     * @param ready
     *            The ready operation.
     */
    public abstract void execute(V ready);
}
