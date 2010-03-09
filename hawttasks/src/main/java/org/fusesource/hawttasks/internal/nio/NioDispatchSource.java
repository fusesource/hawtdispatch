/**************************************************************************************
 * Copyright (C) 2009 Progress Software, Inc. All rights reserved.                    *
 * http://fusesource.com                                                              *
 * ---------------------------------------------------------------------------------- *
 * The software in this package is published under the terms of the AGPL license      *
 * a copy of which has been included with this distribution in the license.txt file.  *
 **************************************************************************************/
package org.fusesource.hawttasks.internal.nio;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.concurrent.atomic.AtomicBoolean;

import org.fusesource.hawttasks.DispatchOption;
import org.fusesource.hawttasks.DispatchQueue;
import org.fusesource.hawttasks.DispatchSource;
import org.fusesource.hawttasks.Dispatcher;
import org.fusesource.hawttasks.actor.ActorProxy;
import org.fusesource.hawttasks.internal.BaseSuspendable;

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

    interface KeyActor {
        public void register();
        public void resume();
        public void addInterest(int ops);
        public void cancel();
    }

    private final SelectableChannel channel;
    private final int interestOps;
    private final DispatchQueue actorQueue;
    private final KeyActor actor;
    private final AtomicBoolean canceled = new AtomicBoolean();

    private volatile DispatchQueue targetQueue;
    private volatile Runnable cancelHandler;
    private volatile Runnable eventHandler;
    private volatile Object context;

    public NioDispatchSource(Dispatcher dispatcher, SelectableChannel channel, int interestOps) {
        if( interestOps == 0 ) {
            throw new IllegalArgumentException("invalid interest ops");
        }
        this.channel = channel;
        this.interestOps = interestOps;
        this.suspended.incrementAndGet();
        this.actorQueue = dispatcher.createSerialQueue(getClass().getName(), DispatchOption.STICK_TO_DISPATCH_THREAD);
        this.actor = ActorProxy.create(KeyActor.class, new KeyActorImpl(), actorQueue);
    }

    @Override
    protected void onStartup() {
        actor.register();
    }
    

    /**
     * All operations on this object are serialized via a serial queue and actor proxy.
     * Additional synchronization is not required. 
     *  
     * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
     */
    final class KeyActorImpl implements KeyActor {
        
        private SelectionKey key;
        private int readyOps;

        public void register() {
            NioSelector selector = NioSelector.CURRENT_SELECTOR.get();
            try {
                key = channel.register(selector.getSelector(), interestOps);
                key.attach(new Runnable() {
                    public void run() {
                        int ops = key.readyOps();
                        debug("selector found ready ops: %d", ops);
                        readyOps |= ops;
                        resume();
                    }
                });
            } catch (ClosedChannelException e) {
                debug(e, "could not register selector");
            }
        }
        
        public void resume() {
            if( readyOps!=0 && suspended.get() <= 0) {
                final int dispatchedOps = readyOps;
                readyOps = 0;
                debug("dispatching for ops: %d", dispatchedOps);
                targetQueue.dispatchAsync(new Runnable() {
                    public void run() {
                        eventHandler.run();
                        actor.addInterest(dispatchedOps);
                    }
                });
            }
        }
        
        public void addInterest(int ops) {
            debug("adding interest: %d", ops);
            key.interestOps(key.interestOps()|ops);
        }
        
        public void cancel() {
            if (key != null && key.isValid()) {
                debug("canceling key.");
                // This will make sure that the key is removed
                // from the selector.
                key.cancel();
                try {
                    NioSelector selector = NioSelector.CURRENT_SELECTOR.get();
                    selector.getSelector().selectNow();
                } catch (IOException e) {
                    debug(e, "Error in close");
                }
                
                if( cancelHandler!=null ) {
                    cancelHandler.run();
                }
            }
        }
    }

    @Override
    protected void onResume() {
        actor.resume();
    }

    @Override
    protected void onShutdown() {
        cancel();
        this.actorQueue.release();
        super.onShutdown();
    }

    public void cancel() {
        if( canceled.compareAndSet(false, true) ) {
            actor.cancel();
        }
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
