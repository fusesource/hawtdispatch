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
package org.fusesource.hawtdispatch.internal;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.fusesource.hawtdispatch.BaseRetained;
import org.fusesource.hawtdispatch.Suspendable;

/**
 * 
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public class BaseSuspendable extends BaseRetained implements Suspendable {

    protected final AtomicBoolean startup = new AtomicBoolean(true);
    protected final AtomicInteger suspended = new AtomicInteger();

    public boolean isSuspended() {
        return suspended.get() > 0;
    }

    public void resume() {
        assertRetained();
        if (suspended.decrementAndGet() == 0) {
            if (startup.compareAndSet(true, false)) {
                onStartup();
            } else {
                onResume();
            }
        }
    }

    public void suspend() {
        assertRetained();
        if (suspended.getAndIncrement() == 0) {
            onSuspend();
        }
    }

    protected void onStartup() {
    }

    protected void onSuspend() {
    }

    protected void onResume() {
    }

}
