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
 * A dispatch source that is used to coalesce multiple application generated
 * events for later processing by the dispatch source event handler.
 *
 * @param <Event>
 * @param <MergedEvent>
 */
public interface CustomDispatchSource<Event, MergedEvent> extends DispatchSource {

    /**
     * <p>
     * Returns pending data for the dispatch source.  Calling this method consumes
     * the event and a subsequent call will return null.
     * </p><p>
     * This function is intended to be called from within the event handler runnable.
     * The result of calling this function outside of the event handler runnable is
     * undefined.
     * </p>
     *
     * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
     */
    public MergedEvent getData();

    /**
     * <p>
     * Merges data into a dispatch source and submits its event handler runnable to its
     * target queue.
     * </p>
     *
     * @param value
     * The value to coalesce with the pending data using the {@link EventAggregator}
     * that was specified when this dispach source was created.
     */
    public void merge(Event value);
}
