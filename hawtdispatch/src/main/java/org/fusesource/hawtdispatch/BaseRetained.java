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

package org.fusesource.hawtdispatch;

import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.String.format;

/**
 * Base class that implements the {@link Retained} interface.
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public class BaseRetained implements Retained {

    private static final int MAX_TRACES = Integer.getInteger("org.fusesource.hawtdispatch.BaseRetained.MAX_TRACES", 100);
    private static final boolean TRACE = Boolean.getBoolean("org.fusesource.hawtdispatch.BaseRetained.TRACE");

    final private AtomicInteger retained = new AtomicInteger(1);
    volatile private Task disposer;
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
    final public void setDisposer(final Runnable disposer) {
        this.setDisposer(new TaskWrapper(disposer));
    }

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
    final public void setDisposer(Task disposer) {
        assertRetained();
        this.disposer = disposer;
    }

    final public Task getDisposer() {
        return disposer;
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
        if( TRACE ) {
            synchronized(traces) {
                assertRetained();
                final int x = retained.incrementAndGet();
                trace("retained", x);
            }
        } else {
            assertRetained();
            retained.getAndIncrement();
        }
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
        if( TRACE ) {
            synchronized(traces) {
                assertRetained();
                final int x = retained.decrementAndGet();
                trace("released", x);
                if (x == 0) {
                    dispose();
                    trace("disposed", x);
                }
            }
        } else {
            assertRetained();
            if (retained.decrementAndGet() == 0) {
                dispose();
            }
        }
    }

    /**
     * <p>
     * Decrements the reference count by n.
     * </p><p>
     * An object is asynchronously disposed once all references are
     * released. Using a disposed object will cause undefined errors.
     * The system does not guarantee that a given client is the last or
     * only reference to a given object.
     * </p>
     * @param n
     */
    final protected void release(int n) {
        if( TRACE ) {
            synchronized(traces) {
                assertRetained();
                int x = retained.addAndGet(-n);
                trace("released "+n, x);
                if ( x == 0) {
                    trace("disposed", x);
                    dispose();
                }
            }
        } else {
            assertRetained();
            if (retained.addAndGet(-n) == 0) {
                dispose();
            }
        }
    }

    /**
     * Subclasses can use this method to validate that the object has not yet been released.
     * it will throw an IllegalStateException if it has been released.
     *
     * @throws IllegalStateException if the object has been released.
     */
    final protected void assertRetained() {
        if( TRACE ){
            synchronized(traces) {
                if( retained.get() <= 0 ) {
                    throw new AssertionError(format("%s: Use of object not allowed after it has been released. %s", this.toString(), traces));
                }
            }
        } else {
            assert retained.get() > 0 : format("%s: Use of object not allowed after it has been released.", this.toString());
        }
    }

    /**
     * @return the retained counter
     */
    final public int retained() {
        return retained.get();
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

    final private ArrayList<String> traces = TRACE ? new ArrayList<String>(MAX_TRACES+1) : null;
    final private void trace(final String action, final     int counter) {
        if( traces.size() < MAX_TRACES) {
            Exception ex = new Exception() {
                public String toString() {
                    return "Trace "+(traces.size()+1)+": "+action+", counter: "+counter+", thread: "+Thread.currentThread().getName();
                }
            };

            String squashed =  squash(ex.getStackTrace());
            if( squashed == null ) {
                StringWriter sw = new StringWriter();
                ex.printStackTrace(new PrintWriter(sw));
                traces.add("\n"+sw);
            }
//            else {
//                traces.add("\n"+ex.toString()+"\n  at "+squashed+"\n");
//            }
        }  else if (traces.size() == MAX_TRACES) {
            traces.add("MAX_TRACES reached... no more traces will be recorded.");
        }
    }

    //
    // Hide system generated balanced calls to retain/release since the tracing facility is
    // here to help end users figure out where THEY are failing to pair up the calls. 
    //
    static private String squash(StackTraceElement[] st) {
        if( st.length > 2) {
            final String traceData = st[2].getClassName()+"."+st[2].getMethodName();
            if( CALLERS.contains(traceData) ) {
                return traceData;
            }
        }
        return null;
    }


    static HashSet<String> CALLERS = new HashSet<String>();
    static {
        if( TRACE ) {
            Properties p = new Properties();
            final InputStream is = BaseRetained.class.getResourceAsStream("BaseRetained.CALLERS");
            try {
                p.load(is);
            } catch (Exception ignore) {
                ignore.printStackTrace();
            } finally {
                try {
                    is.close();
                } catch (Exception ignore) {
                }
            }
            for (Object key : Collections.list(p.keys())) {
                CALLERS.add((String) key);
            }
        }
    }

}
