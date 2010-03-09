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
package org.fusesource.hawtdispatch;

/**
 * Implemented by objects which use a reference counted life cycle.
 *
 * Objects start with a ref count of one.  Retaining the object increments the counter,
 * releasing, decrements the counter.  When the counter reaches zero, the object should
 * not longer be accessed as it will release any resources it needed to perform normal
 * processing.
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public interface Retained {

    /**
     * Increments the reference counter
     */
    public void retain();

    /**
     * Decrements the reference counter
     */
    public void release();

    /**
     * @return true if the reference counter is zero
     */
    public boolean isReleased();

    /**
     * adds a runnable which will be executed once this object's
     * reference counter reaches zero.
     * @param onRelease
     */
    public void addReleaseWatcher(Runnable onRelease);

}
