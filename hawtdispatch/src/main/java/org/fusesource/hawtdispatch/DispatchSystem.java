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
package org.fusesource.hawtdispatch;

import java.nio.channels.SelectableChannel;


/**
 * Provides easy access to a system wide Dispatcher.
 * 
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public class DispatchSystem {

    final public static Dispatcher DISPATCHER = create();

    private static Dispatcher create() {
        return new Dispatcher() {

            final Dispatcher next = new DispatcherConfig().createDispatcher();

            public DispatchQueue getRandomThreadQueue() {
                return next.getRandomThreadQueue();
            }

            public DispatchQueue getRandomThreadQueue(DispatchPriority priority) {
                return next.getRandomThreadQueue(priority);
            }

            public DispatchQueue getGlobalQueue() {
                return next.getGlobalQueue();
            }

            public DispatchQueue getGlobalQueue(DispatchPriority priority) {
                return next.getGlobalQueue(priority);
            }

            public DispatchQueue createSerialQueue(String label, DispatchOption... options) {
                return next.createSerialQueue(label, options);
            }

            public DispatchQueue getMainQueue() {
                return next.getMainQueue();
            }

            public void dispatchMain() {
                next.dispatchMain();
            }

            public DispatchQueue getCurrentQueue() {
                return next.getCurrentQueue();
            }

            public DispatchQueue getCurrentThreadQueue() {
                return next.getCurrentThreadQueue();
            }

            public DispatchSource createSource(SelectableChannel channel, int interestOps, DispatchQueue queue) {
                return next.createSource(channel, interestOps, queue);
            }

            public void addReleaseWatcher(Runnable onRelease) {
            }

            public void retain() {
            }

            public void release() {
            }

            public boolean isReleased() {
                return false;
            }

        };
    }
    
    public static DispatchQueue getMainQueue() {
        return DISPATCHER.getMainQueue();
    }
    
    public static DispatchQueue getGlobalQueue() {
        return DISPATCHER.getGlobalQueue();
    }
    
    public static DispatchQueue getGlobalQueue(DispatchPriority priority) {
        return DISPATCHER.getGlobalQueue(priority);
    }
    
    public static DispatchQueue createSerialQueue(String label, DispatchOption... options) {
        return DISPATCHER.createSerialQueue(label, options);
    }
    
    public static void dispatchMain() {
        DISPATCHER.dispatchMain();
    }

    public static DispatchQueue getCurrentQueue() {
        return DISPATCHER.getCurrentQueue();
    }

    public static DispatchQueue getCurrentThreadQueue() {
        return DISPATCHER.getCurrentThreadQueue();
    }

    public static DispatchSource createSource(SelectableChannel channel, int interestOps, DispatchQueue queue) {
        return DISPATCHER.createSource(channel, interestOps, queue);
    }

    public static DispatchQueue getRandomThreadQueue() {
        return DISPATCHER.getRandomThreadQueue();
    }
}
