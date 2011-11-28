/**
 * Copyright (C) 2010, FuseSource Corp.  All rights reserved.
 */
package org.fusesource.hawtdispatch.internal;

/**
 * <p>
 * </p>
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public class HawtThreadGroup extends ThreadGroup {

    private final HawtDispatcher dispatcher;

    public HawtThreadGroup(HawtDispatcher dispatcher, String s) {
        super(s);
        this.dispatcher = dispatcher;
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        Thread.UncaughtExceptionHandler handler = dispatcher.uncaughtExceptionHandler;
        if( handler!=null ) {
            handler.uncaughtException(thread, throwable);
        } else {
            super.uncaughtException(thread, throwable);
        }
    }
}
