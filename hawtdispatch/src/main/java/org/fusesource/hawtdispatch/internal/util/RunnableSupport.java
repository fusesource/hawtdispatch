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

import java.util.concurrent.atomic.AtomicInteger;

import org.fusesource.hawtdispatch.DispatchQueue;
import org.fusesource.hawtdispatch.Task;
import org.fusesource.hawtdispatch.TaskWrapper;

/**
 * 
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public class RunnableSupport {

    private static Task NO_OP = new Task() {
        public void run() {
        }
        public String toString() {
            return "{}";
        };
    };
    
    public static Task runNoop() {
        return NO_OP;
    }

    public static Task runOnceAfter(final Runnable runnable, int count) {
        return runOnceAfter(new TaskWrapper(runnable), count);
    }

    public static Task runOnceAfter(final Task runnable, int count) {
        if( runnable==null ) {
            return NO_OP;
        }
        if( count == 0 ) {
            runnable.run();
            return NO_OP;
        }
        if( count == 1 ) {
            return runnable;
        }
        final AtomicInteger counter = new AtomicInteger(count);
        return new Task() {
            public void run() {
                if( counter.decrementAndGet()==0 ) {
                    runnable.run();
                }
            }
            public String toString() {
                return "{"+runnable+"}";
            };
        };
    }
    
    public static Task runAfter(final Runnable runnable, int count) {
        return runAfter(new TaskWrapper(runnable), count);
    }
    public static Task runAfter(final Task runnable, int count) {
        if( count <= 0 || runnable==null ) {
            return NO_OP;
        }
        if( count == 1 ) {
            return runnable;
        }
        final AtomicInteger counter = new AtomicInteger(count);
        return new Task() {
            public void run() {
                if( counter.decrementAndGet()<=0 ) {
                    runnable.run();
                }
            }
            public String toString() {
                return "{"+runnable+"}";
            };
        };
    }
    
    public static Task runOnceAfter(final DispatchQueue queue, final Runnable runnable, int count) {
        return runOnceAfter(queue, new TaskWrapper(runnable), count);
    }
    public static Task runOnceAfter(final DispatchQueue queue, final Task task, int count) {
        if( count <= 0 || task==null ) {
            return NO_OP;
        }
        final AtomicInteger counter = new AtomicInteger(count);
        return new Task() {
            public void run() {
                if( counter.decrementAndGet()==0 ) {
                    queue.execute(task);
                }
            }
            public String toString() {
                return "{"+task+"}";
            };
        };
    }
    
    public static Task runAfter(final DispatchQueue queue,  final Runnable runnable, int count) {
        return runAfter(queue, new TaskWrapper(runnable), count);
    }

    public static Task runAfter(final DispatchQueue queue,  final Task task, int count) {
        if( count <= 0 || task==null ) {
            return NO_OP;
        }
        final AtomicInteger counter = new AtomicInteger(count);
        return new Task() {
            public void run() {
                if( counter.decrementAndGet()<=0 ) {
                    queue.execute(task);
                }
            }
            public String toString() {
                return "{"+task.toString()+"}";
            };
        };
    }


}
