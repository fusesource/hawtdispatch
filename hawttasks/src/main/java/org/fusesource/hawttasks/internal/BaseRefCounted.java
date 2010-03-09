/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fusesource.hawttasks.internal;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.fusesource.hawttasks.RefCounted;

import static java.lang.String.*;

/**
 * 
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public class BaseRefCounted implements RefCounted {

    final protected AtomicInteger retained = new AtomicInteger(1);
    final protected ArrayList<Runnable> shutdownHandlers = new ArrayList<Runnable>(1);

    public void addReleaseWatcher(Runnable shutdownHandler) {
        assertRetained();
        synchronized (shutdownHandlers) {
            shutdownHandlers.add(shutdownHandler);
        }
    }

    public void retain() {
        assertRetained();
        retained.getAndIncrement();
    }

    public void release() {
        assertRetained();
        if (retained.decrementAndGet() == 0) {
            onShutdown();
        }
    }

    final protected void assertRetained() {
        if( retained.get() <= 0 ) {
//            System.out.println(format("!!!!!!!! %s: Use of object not allowed after it has been released", this.toString()));
            throw new IllegalStateException(format("%s: Use of object not allowed after it has been released", this.toString()));
        }
//        assert retained.get() > 0 : format("%s: Use of object not allowed after it has been released", this.toString());
    }

    public boolean isReleased() {
        return retained.get() <= 0;
    }

    /**
     * Subclasses should override if they want to do clean up.
     */
    protected void onShutdown() {
        ArrayList<Runnable> copy;
        synchronized (shutdownHandlers) {
            copy = new ArrayList<Runnable>(shutdownHandlers);
            shutdownHandlers.clear();
        }
        for (Runnable runnable : copy) {
            runnable.run();
        }
    }

}
