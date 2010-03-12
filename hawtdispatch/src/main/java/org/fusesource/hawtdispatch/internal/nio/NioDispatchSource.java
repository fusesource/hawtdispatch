/**************************************************************************************
 * Copyright (C) 2009 Progress Software, Inc. All rights reserved.                    *
 * http://fusesource.com                                                              *
 * ---------------------------------------------------------------------------------- *
 * The software in this package is published under the terms of the AGPL license      *
 * a copy of which has been included with this distribution in the license.txt file.  *
 **************************************************************************************/
package org.fusesource.hawtdispatch.internal.nio;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import org.fusesource.hawtdispatch.*;
import org.fusesource.hawtdispatch.internal.BaseSuspendable;

import static java.lang.String.*;

/**
 * SelectableDispatchContext
 * <p>
 * Description:
 * </p>
 * 
 * @author cmacnaug
 * @version 1.0
 */
final public class NioDispatchSource extends BaseSuspendable implements DispatchSource {

    public static final boolean DEBUG = false;

    private final SelectableChannel channel;
    private final DispatchQueue selectorQueue;
    final AtomicBoolean canceled = new AtomicBoolean();
    final int interestOps;

    private DispatchQueue targetQueue;
    private Runnable cancelHandler;
    private Runnable eventHandler;
    private Object context;

    // These fields are only accessed by the selector's thread.
    int readyOps;
    SelectionKey key;
    Attachment attachment;

    public NioDispatchSource(Dispatcher dispatcher, SelectableChannel channel, int interestOps) {
        if( interestOps == 0 ) {
            throw new IllegalArgumentException("invalid interest ops");
        }
        this.channel = channel;
        this.interestOps = interestOps;
        this.suspended.incrementAndGet();
        this.selectorQueue = dispatcher.createSerialQueue(getClass().getName(), DispatchOption.STICK_TO_DISPATCH_THREAD);
    }


    @Override
    protected void onStartup() {
        if( targetQueue==null ) {
            throw new IllegalArgumentException("targetQueue must be set");
        }
        if( eventHandler==null ) {
            throw new IllegalArgumentException("eventHandler must be set");
        }

        // Register the selection key...
        selectorQueue.dispatchAsync(new Runnable(){
            public void run() {
                Selector selector = NioSelector.CURRENT_SELECTOR.get().getSelector();
                try {
                    key = channel.keyFor(selector);
                    if( key==null ) {
                        key = channel.register(selector, interestOps);
                        attachment = new Attachment();
                        key.attach(attachment);
                    } else {
                        attachment = (Attachment)key.attachment();
                    }
                    key.interestOps(key.interestOps()|interestOps);
                    attachment.sources.add(NioDispatchSource.this);
                } catch (ClosedChannelException e) {
                    debug(e, "could not register selector");
                }
            }
        });
    }


    public void cancel() {
        if( canceled.compareAndSet(false, true) ) {
            selectorQueue.dispatchAsync(new Runnable(){
                public void run() {
                    internal_cancel();
                }
            });
        }
    }

    void internal_cancel() {
        // Deregister...
        if (key != null) {

            debug("canceling source");
            attachment.sources.remove(this);

            if( attachment.sources.isEmpty() ) {
                debug("canceling key.");
                // This will make sure that the key is removed
                // from the selector.
                key.cancel();

                // Running a select to remove the canceled key.
                Selector selector = NioSelector.CURRENT_SELECTOR.get().getSelector();
                try {
                    selector.selectNow();
                } catch (IOException e) {
                    debug(e, "Error canceling");
                }
            }

        }
        targetQueue.release();
        if( cancelHandler!=null ) {
            cancelHandler.run();
        }
    }

    public void fire() {
        if( readyOps!=0 && !isSuspended() && !isCanceled() ) {
            readyOps = 0;
            targetQueue.dispatchAsync(new Runnable() {
                public void run() {
                    if( !isSuspended() && !isCanceled()) {
                        debug("fired %d", interestOps);
                        eventHandler.run();
                        updateInterest();
                    }
                }
            });
        }
    }

    private void updateInterest() {
        selectorQueue.dispatchAsync(new Runnable(){
            public void run() {
                if( !isSuspended() && !isCanceled() ) {
                    debug("adding interest: %d", interestOps);
                    if( key.isValid() ) {
                        key.interestOps(key.interestOps()|interestOps);
                    }
                }
            }
        });
    }

    @Override
    protected void onSuspend() {
        debug("onSuspend");
        super.onSuspend();
    }

    @Override
    protected void onResume() {
        debug("onResume");
        readyOps = interestOps;
        fire();
    }

    @Override
    protected void onShutdown() {
        cancel();
        selectorQueue.dispatchAsync(new Runnable(){
            public void run() {
                NioDispatchSource.super.onShutdown();
                selectorQueue.release();
            }
        });
    }

    public boolean isCanceled() {
        return canceled.get();
    }

    public void setCancelHandler(Runnable cancelHandler) {
        this.cancelHandler = cancelHandler;
    }

    public void setEventHandler(Runnable eventHandler) {
        this.eventHandler = eventHandler;
    }

    @SuppressWarnings("unchecked")
    public <Context> Context getContext() {
        return (Context) context;
    }

    public <Context> void setContext(Context context) {
        this.context = context;
    }

    public void setTargetQueue(DispatchQueue targetQueue) {
        if( this.targetQueue !=null ) {
            this.targetQueue.release();
        }
        this.targetQueue = targetQueue;
        if( this.targetQueue !=null ) {
            this.targetQueue.retain();
        }
    }

    public DispatchQueue getTargetQueue() {
        return this.targetQueue;
    }

    protected void debug(String str, Object... args) {
        if (DEBUG) {
            System.out.println(format("[DEBUG] NioDispatchSource %0#10x: ", System.identityHashCode(this))+format(str, args));
        }
    }

    protected void debug(Throwable thrown, String str, Object... args) {
        if (DEBUG) {
            if (str != null) {
                debug(str, args);
            }
            if (thrown != null) {
                thrown.printStackTrace();
            }
        }
    }

}
