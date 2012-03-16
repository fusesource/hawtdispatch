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

import java.util.LinkedList;
import java.util.concurrent.Executor;

import static org.fusesource.hawtdispatch.Dispatch.*;

/**
 * Sends runnable tasks to a DispatchQueue via a an EventAggregator
 * so that they first batch up on the sender side before being
 * sent to the DispatchQueue which then executes that tasks.
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public class AggregatingExecutor implements Executor {

    final DispatchQueue queue;
    final CustomDispatchSource<Runnable, LinkedList<Runnable>> source;

    public AggregatingExecutor(DispatchQueue queue) {
        this.queue = queue;
        this.source = createSource(EventAggregators.<Runnable>linkedList(), queue);
        this.source.setEventHandler(new Task() {
            public void run() {
                for (Runnable runnable: source.getData() ) {
                    try {
                        runnable.run();
                    } catch (Exception e) {
                      Thread thread = Thread.currentThread();
                      thread.getUncaughtExceptionHandler().uncaughtException(thread, e);
                    }
                }
            }
        });
        this.source.resume();
    }


    public void suspend() {
        source.suspend();
    }

    public void resume() {
        source.resume();
    }

    public void execute(Runnable task) {
        if (getCurrentQueue() == null) {
            queue.execute(new TaskWrapper(task));
        } else {
            source.merge(task);
        }
    }

}