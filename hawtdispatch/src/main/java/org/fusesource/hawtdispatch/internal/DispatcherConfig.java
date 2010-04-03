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

import org.fusesource.hawtdispatch.internal.HawtDispatcher;

/**
 * 
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public class DispatcherConfig {
    
    private String label="default";
    private int threads=Runtime.getRuntime().availableProcessors();

    public static Dispatcher create(String name, int threads) {
        DispatcherConfig config = new DispatcherConfig();
        config.label=name;
        config.threads=threads;
        return config.createDispatcher();
    }

    public Dispatcher createDispatcher() {
        return new HawtDispatcher(this);
    }
    
    public String getLabel() {
        return label;
    }

    public void setLabel(String name) {
        this.label = name;
    }

    public int getThreads() {
        return threads;
    }

    public void setThreads(int threads) {
        this.threads = threads;
    }


}
