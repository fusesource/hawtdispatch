/**
 * Copyright (c) 2008-2009 Apple Inc. All rights reserved.
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

/**
 * <p>
 * The EventAggregator interface is used by the {@link CustomDispatchSource} objects to handle
 * coalescing data before passing it to the application.  Implementations of this class should
 * be stateless to remain thread-safe.  You can also use one of several built in implementations:
 * </p>
 *
 * <ul>
 * <li>{@link EventAggregators#INTEGER_ADD}</li>
 * <li>{@link EventAggregators#INTEGER_OR}</li>
 * <li>{@link EventAggregators#LONG_ADD}</li>
 * <li>{@link EventAggregators#LONG_OR}</li> 
 * </ul>
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public interface EventAggregator<Event, MergedEvent> {

    /**
     * <p>
     * Merge the given event with the previous event values.
     * </p>
     *
     * @param previous may be null
     * @param event the value that should be merged
     * @return a newly merged result
     */
    public MergedEvent mergeEvent(MergedEvent previous, Event event);

    /**
     * <p>
     * Merge the given events with the previous event values.
     * </p>
     *
     * @param previous the value of previous merges
     * @param events the value of more merges
     * @return a newly merged result
     */
    public MergedEvent mergeEvents(MergedEvent previous, MergedEvent events);

}
