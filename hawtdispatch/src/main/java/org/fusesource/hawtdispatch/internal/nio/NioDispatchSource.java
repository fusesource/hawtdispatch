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
    private final int interestOps;
    private final DispatchQueue selectorQueue;
    private final AtomicBoolean canceled = new AtomicBoolean();

    private DispatchQueue targetQueue;
    private Runnable cancelHandler;
    private Runnable eventHandler;
    private Object context;

    // These feilds are only accessed by the selector's thread.
    private int readyOps;
    private SelectionKey key;
    private Attachment attachment;

    public NioDispatchSource(Dispatcher dispatcher, SelectableChannel channel, int interestOps) {
        if( interestOps == 0 ) {
            throw new IllegalArgumentException("invalid interest ops");
        }
        this.channel = channel;
        this.interestOps = interestOps;
        this.suspended.incrementAndGet();
        this.selectorQueue = dispatcher.createSerialQueue(getClass().getName(), DispatchOption.STICK_TO_DISPATCH_THREAD);
    }

    /**
     * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
     */
    static final class Attachment implements Runnable {
        private final SelectionKey key;
        private final ArrayList<NioDispatchSource> sources = new ArrayList<NioDispatchSource>(2);

        public Attachment(SelectionKey key) {
            this.key = key;
        }
        
        public void run() {
            int readyOps = key.readyOps();
            for(NioDispatchSource source: sources) {
                int ops = source.interestOps & readyOps;
                if( ops !=0 ) {
                    source.readyOps |= readyOps;
                    source.fire();
                }
            }
        }
    }

    @Override
    protected void onStartup() {

        // Register the selection key...
        selectorQueue.dispatchAsync(new Runnable(){
            public void run() {
                Selector selector = NioSelector.CURRENT_SELECTOR.get().getSelector();
                try {
                    key = channel.keyFor(selector);
                    if( key==null ) {
                        key = channel.register(selector, interestOps);
                        attachment = new Attachment(key);
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
            // Deregister...
            selectorQueue.dispatchAsync(new Runnable(){
                public void run() {
                    if (key != null) {
                        attachment.sources.remove(NioDispatchSource.this);

                        if( attachment.sources.isEmpty() && key.isValid() ) {
                            debug("canceling key.");
                            // This will make sure that the key is removed
                            // from the selector.
                            key.cancel();
                            try {
                                // Running a select to remove the canceled key.
                                Selector selector = NioSelector.CURRENT_SELECTOR.get().getSelector();
                                selector.selectNow();
                            } catch (IOException e) {
                                debug(e, "Error canceling");
                            }
                        }

                    }
                    if( cancelHandler!=null ) {
                        cancelHandler.run();
                    }
                }
            });
        }
    }

    public void fire() {
        if( readyOps!=0 && suspended.get() <= 0) {
            final int dispatchedOps = readyOps;
            readyOps = 0;
            debug("dispatching for ops: %d", dispatchedOps);
            targetQueue.dispatchAsync(new Runnable() {
                public void run() {
                    eventHandler.run();
                    selectorQueue.dispatchAsync(new Runnable(){
                        public void run() {
                            debug("adding interest: %d", dispatchedOps);
                            key.interestOps(key.interestOps()|dispatchedOps);
                        }
                    });
                }
            });
        }
    }



    @Override
    protected void onResume() {
        selectorQueue.dispatchAsync(new Runnable(){
            public void run() {
                fire();
            }
        });
    }

    @Override
    protected void onShutdown() {
        cancel();
        this.selectorQueue.release();
        super.onShutdown();
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
        this.targetQueue = targetQueue;
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
