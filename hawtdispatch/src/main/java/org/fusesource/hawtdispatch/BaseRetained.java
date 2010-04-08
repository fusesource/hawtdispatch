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

import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.String.format;

/**
 * Base class that implements the {@link Retained} interface.
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public class BaseRetained implements Retained {

    private static final boolean TRACE = false;

    final protected AtomicInteger retained = new AtomicInteger(1);
    volatile protected Runnable disposer;

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
    final public void setDisposer(Runnable disposer) {
        assertRetained();
        this.disposer = disposer;
    }

    /**
     * <p>
     * Increment the reference count of this object.
     * </p>
     *
     * Calls to {@link #retain()} must be balanced with calls to
     * {@link #release()}.
     */
    final public void retain() {
        assertRetained();
        retained.getAndIncrement();
        trace("retained at:");
    }

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
    final public void release() {
        assertRetained();
        if (retained.decrementAndGet() == 0) {
            dispose();
        }
        trace("released at:");
    }

    /**
     * Subclasses can use this method to validate that the object has not yet been released.
     * it will throw an IllegalStateException if it has been released.
     *
     * @throws IllegalStateException if the object has been released.
     */
    final protected void assertRetained() {
        if( TRACE ){
            if( retained.get() <= 0 ) {
                throw new IllegalStateException(format("%s: Use of object not allowed after it has been released.", this.toString()));
            }
        } else {
            assert retained.get() > 0 : format("%s: Use of object not allowed after it has been released.", this.toString());
        }
    }

    /**
     * @return true if the retain counter is zero
     */
    final public boolean isReleased() {
        return retained.get() <= 0;
    }

    /**
     * <p>
     * This method will be called once the release retained reaches zero.  It causes
     * the set disposer runnabled to be executed if not null.
     * <p></p>
     * Subclasses should override if they want to implement a custom disposing action in
     * addition to the actions performed by the disposer object.
     * </p>
     */
    protected void dispose() {
        Runnable disposer = this.disposer;
        if( disposer!=null ) {
            disposer.run();
        }
    }


//    final protected ArrayList<String> traces = TRACE ? new ArrayList<String>() : null;
    final private void trace(final String message) {
        if( TRACE ) {
//            StringWriter sw = new StringWriter();
//            new Exception() {
//                public String toString() {
//                    return "Trace "+(traces.size()+1)+": "+message+", retain counter: "+retained.get();
//                }
//            }.printStackTrace(new PrintWriter(sw));
//            traces.add("\n"+sw);
        }
    }
}
