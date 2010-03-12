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

    final private static Dispatcher dispatcher = create();

    private static Dispatcher create() {
        Dispatcher rc = new DispatcherConfig().createDispatcher();
        rc.resume();
        return rc;
    }
    
    public static DispatchQueue getMainQueue() {
        return dispatcher.getMainQueue();
    }
    
    public static DispatchQueue getGlobalQueue() {
        return dispatcher.getGlobalQueue();
    }
    
    public static DispatchQueue getGlobalQueue(DispatchPriority priority) {
        return dispatcher.getGlobalQueue(priority);
    }
    
    public static DispatchQueue createSerialQueue(String label, DispatchOption... options) {
        return dispatcher.createSerialQueue(label, options);
    }
    
    public static void dispatchMain() {
        dispatcher.dispatchMain();
    }

    public static DispatchQueue getCurrentQueue() {
        return dispatcher.getCurrentQueue();
    }

    public static DispatchQueue getCurrentThreadQueue() {
        return dispatcher.getCurrentThreadQueue();
    }

    public static DispatchSource createSource(SelectableChannel channel, int interestOps, DispatchQueue queue) {
        return dispatcher.createSource(channel, interestOps, queue);
    }

    public static DispatchQueue getRandomThreadQueue() {
        return dispatcher.getRandomThreadQueue();
    }
}
