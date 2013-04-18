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

package org.fusesource.hawtdispatch.internal;

import org.fusesource.hawtdispatch.Dispatcher;

/**
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public class DispatcherConfig {

    private static HawtDispatcher defaultDispatcher;

    synchronized public static HawtDispatcher getDefaultDispatcher() {
        if( defaultDispatcher == null ) {
            defaultDispatcher = new DispatcherConfig().createDispatcher();
        }
        return defaultDispatcher;
    }

    private String label="hawtdispatch";
    private int threads = Integer.getInteger("hawtdispatch.threads", Runtime.getRuntime().availableProcessors());
    private boolean profile = Boolean.getBoolean("hawtdispatch.profile");
    private int drains = Integer.getInteger("hawtdispatch.drains", 1000);
    private boolean jmx = "true".equals(System.getProperty("hawtdispatch.jmx", "true").toLowerCase());

    public static Dispatcher create(String name, int threads) {
        DispatcherConfig config = new DispatcherConfig();
        config.label=name;
        config.threads=threads;
        return config.createDispatcher();
    }

    public HawtDispatcher createDispatcher() {
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

    public boolean isProfile() {
        return profile;
    }

    public void setProfile(boolean profile) {
        this.profile = profile;
    }

    public int getDrains() {
        return drains;
    }

    public void setDrains(int drains) {
        this.drains = drains;
    }

    public boolean isJmx() {
        return jmx;
    }

    public void setJmx(boolean jmx) {
        this.jmx = jmx;
    }
}
