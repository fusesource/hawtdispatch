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

package org.fusesource.hawtdispatch.internal;

import org.fusesource.hawtdispatch.*;

import java.io.IOException;
import java.nio.channels.*;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.String.format;
import static org.fusesource.hawtdispatch.DispatchQueue.QueueType.THREAD_QUEUE;

/**
 * <p>
 * Implements the DispatchSource interface.
 * </p>
 * <p>
 * Description: An NioDispatchSource is associated with one SelectableChannel
 * but supports being registered on selectors associated with different thread.
 * Usually just one at time tho.
 * </p>
 * 
 * @author cmacnaug
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
final public class NioDispatchSource extends AbstractDispatchObject implements DispatchSource {

    public static final boolean DEBUG = false;

    final SelectableChannel channel;
    volatile DispatchQueue selectorQueue;

    final AtomicBoolean canceled = new AtomicBoolean();
    final int interestOps;

    Task cancelHandler;
    Task eventHandler;

    // These fields are only accessed by the ioManager's thread.
    public static class KeyState {
        int readyOps;
        final NioAttachment attachment;

        public SelectionKey key() {
            return attachment.key();
        }

        public KeyState(NioAttachment attachment) {
            this.attachment = attachment;
        }

        @Override
        public String toString() {
            return "{ready: "+opsToString(readyOps)+" }";
        }
    }

    private static String opsToString(int ops) {
        ArrayList sb = new ArrayList();
        if( (ops & SelectionKey.OP_ACCEPT) != 0) {
            sb.add("ACCEPT");
        }
        if( (ops & SelectionKey.OP_CONNECT) != 0) {
            sb.add("CONNECT");
        }
        if( (ops & SelectionKey.OP_READ) != 0) {
            sb.add("READ");
        }
        if( (ops & SelectionKey.OP_WRITE) != 0) {
            sb.add("WRITE");
        }
        return sb.toString();
    }

    final ThreadLocal<KeyState> keyState = new ThreadLocal<KeyState>();

    public NioDispatchSource(HawtDispatcher dispatcher, SelectableChannel channel, int interestOps, DispatchQueue targetQueue) {
        if( interestOps == 0 ) {
            throw new IllegalArgumentException("invalid interest ops");
        }
        this.channel = channel;
        this.selectorQueue = pickThreadQueue(dispatcher, targetQueue);
        this.interestOps = interestOps;
        this.suspended.incrementAndGet();
        this.setTargetQueue(targetQueue);
    }


    static private DispatchQueue pickThreadQueue(HawtDispatcher dispatcher, DispatchQueue targetQueue) {
        // Try to select a thread queue associated /w the target if available..
        DispatchQueue selectorQueue = targetQueue;
        while( selectorQueue.getQueueType()!=THREAD_QUEUE  && selectorQueue.getTargetQueue() !=null ) {
            selectorQueue = selectorQueue.getTargetQueue();
        }

        // otherwise.. pick the thread queue with the fewest registered selection
        // keys.
        if( selectorQueue.getQueueType()!=THREAD_QUEUE ) {

            WorkerThread[] threads = dispatcher.DEFAULT_QUEUE.workers.getThreads();
            WorkerThread min = threads[0];
            int minSize = min.getNioManager().getRegisteredKeyCount();
            for( int i=1; i < threads.length; i++) {
                int s = threads[i].getNioManager().getRegisteredKeyCount();
                if( s < minSize ) {
                    minSize = s;
                    min = threads[i];
                }
            }
            selectorQueue = min.getDispatchQueue();
        }

        return selectorQueue;
    }

    @Override
    protected void onStartup() {
        if( eventHandler==null ) {
            throw new IllegalArgumentException("eventHandler must be set");
        }
        register_on(selectorQueue);
    }

    public void cancel() {
        if( canceled.compareAndSet(false, true) ) {
            selectorQueue.execute(new Task(){
                public void run() {
                    internal_cancel();
                }
            });
        }
    }

    void internal_cancel() {
        key_cancel();
        if( cancelHandler!=null ) {
            targetQueue.execute(cancelHandler);
        }
    }

    private NioManager getCurrentNioManager() {
        return WorkerThread.currentWorkerThread().getNioManager();
    }

    private void key_cancel() {
        // Deregister...
        KeyState state = keyState.get();
        if( state==null ) {
            return;
        }
        debug("canceling source");
        state.attachment.sources.remove(this);
        if( state.attachment.sources.isEmpty() ) {
            debug("canceling key.");
            getCurrentNioManager().cancel(state.key());
        }
        keyState.remove();
    }

    private void register_on(final DispatchQueue queue) {
        queue.execute(new Task(){
            public void run() {
                assert keyState.get()==null;
                if(DEBUG) debug("Registering interest %s", opsToString(interestOps));
                try {
                    NioAttachment attachment = getCurrentNioManager().register(channel, interestOps);
                    attachment.sources.add(NioDispatchSource.this);
                    keyState.set(new KeyState(attachment));

                } catch (ClosedChannelException e) {
                    debug(e, "could not register with selector");
                }
                debug("Registered");
            }
        });
    }


    public void fire(final int readyOps) {
        final KeyState state = keyState.get();
        if( state==null ) {
            return;
        }
        state.readyOps |= readyOps;
        if( state.readyOps!=0  && !isSuspended()&& !isCanceled() ) {
            state.readyOps = 0;
            targetQueue.execute(new Task() {
                public void run() {
                    if( !isSuspended() && !isCanceled()) {
                        if(DEBUG) debug("fired %s", opsToString(readyOps));
                        try {
                            eventHandler.run();
                        } catch (Throwable e) {
                          Thread thread = Thread.currentThread();
                          thread.getUncaughtExceptionHandler().uncaughtException(thread, e);
                        }
                        updateInterest();
                    }
                }
            });
        }
    }

    private Task updateInterestTask = new Task(){
        public void run() {
            if( !isSuspended() && !isCanceled() ) {
                if(DEBUG) debug("adding interest: %d", opsToString(interestOps));
                KeyState state = keyState.get();
                if( state==null ) {
                    return;
                }

                SelectionKey key = state.key();
                try {
                    key.interestOps(key.interestOps() | interestOps);
                } catch(CancelledKeyException e) {
                    internal_cancel();
                }
            }
        }
    };

    private void updateInterest() {
        if( isCurrent(selectorQueue) ) {
            updateInterestTask.run();
        } else {
            selectorQueue.execute(updateInterestTask);
        }
    }

    private boolean isCurrent(DispatchQueue q) {
        WorkerThread thread = WorkerThread.currentWorkerThread();
        if( thread == null )
            return false;
        return thread.getDispatchQueue() == q;
    }

    @Override
    protected void onSuspend() {
        debug("onSuspend");
        super.onSuspend();
    }

    @Override
    protected void onResume() {
        debug("onResume");
        if( isCurrent(selectorQueue) ) {
            KeyState state = keyState.get();
            if( state==null || state.readyOps==0 ) {
                updateInterest();
            } else {
                fire(state.readyOps);
            }
        } else {
            selectorQueue.execute(new Task(){
                public void run() {
                    KeyState state = keyState.get();
                    if( state==null || state.readyOps==0 ) {
                        updateInterest();
                    } else {
                        fire(interestOps);
                    }
                }
            });
        }
    }

    public boolean isCanceled() {
        return canceled.get();
    }

    @Deprecated
    public void setCancelHandler(Runnable handler) {
        this.setCancelHandler(new TaskWrapper(handler));
    }

    @Deprecated
    public void setEventHandler(Runnable handler) {
        this.setEventHandler(new TaskWrapper(handler));
    }

    public void setCancelHandler(Task cancelHandler) {
        this.cancelHandler = cancelHandler;
    }

    public void setEventHandler(Task eventHandler) {
        this.eventHandler = eventHandler;
    }

    public Void getData() {
        return null;
    }

    public void setTargetQueue(DispatchQueue next) {
        super.setTargetQueue(next);

        // The target thread queue might be different. Optimize by switching the selector to it.
        // Do we need to switch selector threads?
        DispatchQueue queue = next;
        while( queue.getQueueType()!=THREAD_QUEUE  && queue.getTargetQueue() !=null ) {
            queue = queue.getTargetQueue();
        }
        if( queue.getQueueType()==THREAD_QUEUE && queue!=selectorQueue ) {
            DispatchQueue previous = selectorQueue;
            final DispatchQueue newQueue = queue;
            debug("Switching to " + newQueue.getLabel());
            selectorQueue = queue;
            if( previous!=null ) {
                previous.execute(new Task(){
                    public void run() {
                        key_cancel();
                        register_on(newQueue);
                    }
                });
            } else {
                register_on(newQueue);
            }
        }
    }

    protected void debug(String str, Object... args) {
        if (DEBUG) {
            String thread = Thread.currentThread().getName();
            String target ="";
            if( Dispatch.getCurrentQueue()!=null ) {
                target = Dispatch.getCurrentQueue().getLabel() + " | ";
            }
            System.out.println(format("DEBUG | %s | #%0#10x | %s%s", thread, System.identityHashCode(this), target, format(str, args)));
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
