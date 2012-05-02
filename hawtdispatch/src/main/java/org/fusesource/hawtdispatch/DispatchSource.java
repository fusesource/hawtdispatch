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
 * A dispatch source is used to monitor low-level system objects and
 * automatically submit a handler runnable to a dispatch queue in response
 * to events.
 * </p><p>
 * Dispatch sources are not reentrant. Any events received while the dispatch
 * source is suspended or while the event handler runnable is currently executing
 * will be coalesced and delivered after the dispatch source is resumed or the
 * event handler runnable has returned.
 * </p><p>
 * Dispatch sources are created in a suspended state. After creating the
 * source and setting any desired attributes (i.e. the handlers),
 * a call must be made to the dispatch source's <code>resume()</code> method
 * in order to begin event delivery.
 * </p>
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public interface DispatchSource extends DispatchObject {

    /**
     * <p>
     * Sets the cancellation handler runnable for the given dispatch source.
     * </p><p>
     * The cancellation handler (if specified) will be submitted to the source's
     * target queue in response to a call to {@link #cancel()} once the
     * system has released all references to the source's underlying handle and
     * the source's event handler runnable has returned.
     * </p>
     *
     * @param handler
     * The cancellation handler runnable to submit to the source's target queue.
     */
    public void setCancelHandler(Runnable handler);

    /**
     * <p>
     * Sets the event handler runnable of this dispatch source.
     * </p>
     *
     * @param handler
     * The event handler runnable to submit to the source's target queue.
     */
    public void setEventHandler(Runnable handler);

    /**
     * <p>
     * Sets the cancellation handler task for the given dispatch source.
     * </p><p>
     * The cancellation handler (if specified) will be submitted to the source's
     * target queue in response to a call to {@link #cancel()} once the
     * system has released all references to the source's underlying handle and
     * the source's event handler runnable has returned.
     * </p>
     *
     * @param task
     * The cancellation handler runnable to submit to the source's target queue.
     */
    public void setCancelHandler(Task task);

    /**
     * <p>
     * Sets the event handler task of this dispatch source.
     * </p>
     *
     * @param task
     * The event handler runnable to submit to the source's target queue.
     */
    public void setEventHandler(Task task);

    /**
     * <p>
     * Asynchronously cancel the dispatch source, preventing any further invocation
     * of its event handler runnable.
     * </p><p>
     * Cancellation prevents any further invocation of the event handler runnable for
     * the specified dispatch source, but does not interrupt an event handler
     * runnable that is already in progress.
     * </p><p>
     * The cancellation handler is submitted to the source's target queue once the
     * the source's event handler has finished, indicating it is now safe to close
     * the source's handle.
     * </p>
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
