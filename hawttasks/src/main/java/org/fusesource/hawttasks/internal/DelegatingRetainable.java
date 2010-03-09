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

import org.fusesource.hawttasks.Retainable;

import static java.lang.String.*;

/**
 * 
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public class DelegatingRetainable implements Retainable {

    protected final Retainable retainable;

    public DelegatingRetainable(Retainable retainable) {
        this.retainable = retainable;
    }

    public void addShutdownWatcher(Runnable shutdownWatcher) {
        retainable.addShutdownWatcher(shutdownWatcher);
    }

    public void release() {
        retainable.release();
    }

    public void retain() {
        retainable.retain();
    }

    public boolean isShutdown() {
        return retainable.isShutdown();
    }

    final protected void assertRetained() {
//        if( retained.get() <= 0 ) {
//            throw new IllegalStateException(format("%s: Use of object not allowed after it has been released", this.toString()));
//        }
        assert !isShutdown() : format("Use of object not allowed after it has been released: %s", this.toString());
    }

}
