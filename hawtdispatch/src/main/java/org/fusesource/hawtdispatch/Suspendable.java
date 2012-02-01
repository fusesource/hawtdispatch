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
 * Implemented by dispatch objects which can suspend the
 * execution of dispatch tasks.
 * </p>
 */
public interface Suspendable {

    /**
     * <p>
     * Suspends the invocation of tasks on a dispatch object.
     * </p><p>
     * A suspended object will not invoke any tasks associated with it. The
     * suspension of an object will occur after any running runnable associated with
     * the object completes.
     * </p><p>
     * Calls to {@link #suspend()} must be balanced with calls
     * to {@link #resume()}.
     * </p>
     *
     * @see #resume()
     */
    public void suspend();

    /**
     * <p>
     * Resumes the invocation of tasks on a dispatch object.
     * </p>
     *
     * @see #suspend()
     */
    public void resume();

    /**
     * @see #resume()
     * @see #suspend()
     * @return true if the the current object is suspended.
     */
    public boolean isSuspended();
    
}
