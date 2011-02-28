/**
 * Copyright (c) 2008-2009 Apple Inc. All rights reserved.
 * Copyright (C) 2009-2010, Progress Software Corporation and/or its
 * subsidiaries or affiliates.  All rights reserved.

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
 * <p>
 * Implemented by dispatch objects which use a reference counted life cycle.
 * </p><p>
 * Dispatch objects start with a retained count of one.  Retaining the object increments the retain counter,
 * releasing, decrements the counter.  When the counter reaches zero, the object should
 * not longer be accessed as it will release any resources it needs to perform normal
 * processing.
 * </p>
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public interface Retained {

    /**
     * <p>
     * Increment the reference count of this object.
     * </p>
     *
     * Calls to {@link #retain()} must be balanced with calls to
     * {@link #release()}.
     */
    public void retain();

    /**
     * <p>
     * Decrement the reference count of this object.
     * </p><p>
     * An object is asynchronously disposed once all references are
     * released. Using a disposed object will cause undefined errors.
     * The system does not guarantee that a given client is the last or
     * only reference to a given object.
     * </p>
     */
    public void release();

    /**
     * @return the retained counter
     */
    public int retained();


    /**
     * <p>
     * Adds a disposer runnable that is executed once the object is disposed.
     * </p><p>
     * A dispatch object's disposer runnable will be invoked on the object's target queue
     * once the object's retain counter reaches zero. This disposer may be
     * used by the application to release any resources associated with the object.
     * </p>
     *
     * @param disposer
     */
//    public void setDisposer(Runnable disposer);

}
