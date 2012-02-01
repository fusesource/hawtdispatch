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
 * Implemented by dispatch objects that can be configured with a target queue
 * that it uses for executing the object's asynchronous tasks.
 * </p>
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public interface DispatchObject extends Suspendable {

    /**
     * <p>
     * Sets the target queue for this object.
     * </p><p>
     * An object's target queue is responsible for processing the object.
     * </p><p>
     * A dispatch queue's priority is inherited by its target queue. Use the
     * {@link Dispatch#getGlobalQueue()} method to obtain suitable target queue
     * of the desired priority.
     * </p><p>
     * A dispatch source's target queue specifies where its event handler and
     * cancellation handler runnables will be submitted.
     * </p>
     *
     * @param       queue
     * The new target queue for the object. The queue is retained, and the
     * previous one, if any, is released.
     * The result of passing NULL in this parameter is undefined.
     */
    public void setTargetQueue(DispatchQueue queue);

    /**
     * <p>
     * Gets the target queue for this object.
     * </p>
     *
     * @see #setTargetQueue(DispatchQueue)  
     * @return the target queue of this object.
     */
    public DispatchQueue getTargetQueue();
    
}
