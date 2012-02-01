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
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

import java.util.Hashtable;

/**
 * OSGi integration point.
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public class Activator implements BundleActivator {

    public void start(BundleContext context) throws Exception {
        try {
            DispatcherConfig.getDefaultDispatcher().restart();
        } catch (IllegalStateException ignore) {
            // dispatchers initial state is running.. so a restart
            // only works after it's been shutdown.
        }
        context.registerService(Dispatcher.class.getName(), DispatcherConfig.getDefaultDispatcher(), new Hashtable());
    }

    public void stop(BundleContext context) throws Exception {
        DispatcherConfig.getDefaultDispatcher().shutdown();
    }

}
