/**
 * Copyright (c) 2008-2009 Apple Inc. All rights reserved.
 * Copyright (C) 2009-2010, Progress Software Corporation and/or its
 * subsidiaries or affiliates.  All rights reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fusesource.hawtdispatch;

/**
 * A dispatch source is used to monitor low-level system objects and
 * automatically submit a handler block to a dispatch queue in response
 * to events.
 *
 * Dispatch sources are not reentrant. Any events received while the dispatch
 * source is suspended or while the event handler block is currently executing
 * will be coalesced and delivered after the dispatch source is resumed or the
 * event handler block has returned.
 *
 * Dispatch sources are created in a suspended state. After creating the
 * source and setting any desired attributes (i.e. the handlers),
 * a call must be made to the dispatch source's <code>resume()</code> method
 * in order to begin event delivery.
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public interface DispatchSource extends DispatchObject {

    /**
     * Sets the cancellation handler runnable for the given dispatch source.
     * <br/>
     * The cancellation handler (if specified) will be submitted to the source's
     * target queue in response to a call to {@link #cancel()} once the
     * system has released all references to the source's underlying handle and
     * the source's event handler runnable has returned.
     *
     * @param handler
     * The cancellation handler block to submit to the source's target queue.
     */
    public void setCancelHandler(Runnable handler);

    /**
     * Sets the event handler runnable of this dispatch source.
     *
     * @param handler
     * The event handler runnable to submit to the source's target queue.
     */
    public void setEventHandler(Runnable handler);

    /**
     * Asynchronously cancel the dispatch source, preventing any further invocation
     * of its event handler runnable.
     * <br/>
     * Cancellation prevents any further invocation of the event handler runnable for
     * the specified dispatch source, but does not interrupt an event handler
     * block that is already in progress.
     * <br/>
     * The cancellation handler is submitted to the source's target queue once the
     * the source's event handler has finished, indicating it is now safe to close
     * the source's handle.
     *
     * @see #setCancelHandler(Runnable) 
     */
    public void cancel();

    /**
     * @see #cancel()  
     * @return true if the dispatch source has been canceled.
     */
    public boolean isCanceled();

}
