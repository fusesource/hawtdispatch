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

package org.fusesource.hawtdispatch.transport;

import org.fusesource.hawtdispatch.DispatchQueue;
import org.fusesource.hawtdispatch.Task;
import org.fusesource.hawtdispatch.TaskWrapper;

import java.util.LinkedList;

/**
 * <p>
 * The BaseService provides helpers for dealing async service state.
 * </p>
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public abstract class ServiceBase {

    public static class State {
        public String toString() {
            return getClass().getSimpleName();
        }
        public boolean isStarted() {
            return false;
        }
        public boolean isStarting() {
            return false;
        }
    }

    static class CallbackSupport extends State {
        LinkedList<Task> callbacks = new LinkedList<Task>();

        void add(Task r) {
            if (r != null) {
                callbacks.add(r);
            }
        }

        void done() {
            for (Task callback : callbacks) {
                callback.run();
            }
        }
    }

    public static final State CREATED = new State();
    public static class STARTING extends CallbackSupport {
        public boolean isStarting() {
            return true;
        }
    }
    public static final State STARTED = new State() {
        public boolean isStarted() {
            return true;
        }
    };
    public static class STOPPING extends CallbackSupport {
    }

    public static final State STOPPED = new State();


    protected State _serviceState = CREATED;

    final public void start(final Runnable onCompleted) {
        start(new TaskWrapper(onCompleted));
    }

    final public void start(final Task onCompleted) {
        getDispatchQueue().execute(new Task() {
            public void run() {
                if (_serviceState == CREATED ||
                        _serviceState == STOPPED) {
                    final STARTING state = new STARTING();
                    state.add(onCompleted);
                    _serviceState = state;
                    _start(new Task() {
                        public void run() {
                            _serviceState = STARTED;
                            state.done();
                        }
                    });
                } else if (_serviceState instanceof STARTING) {
                    ((STARTING) _serviceState).add(onCompleted);
                } else if (_serviceState == STARTED) {
                    if (onCompleted != null) {
                        onCompleted.run();
                    }
                } else {
                    if (onCompleted != null) {
                        onCompleted.run();
                    }
                    error("start should not be called from state: " + _serviceState);
                }
            }
        });
    }

    final public void stop(final Runnable onCompleted) {
        stop(new TaskWrapper(onCompleted));
    }

    final public void stop(final Task onCompleted) {
        getDispatchQueue().execute(new Task() {
            public void run() {
                if (_serviceState == STARTED) {
                    final STOPPING state = new STOPPING();
                    state.add(onCompleted);
                    _serviceState = state;
                    _stop(new Task() {
                        public void run() {
                            _serviceState = STOPPED;
                            state.done();
                        }
                    });
                } else if (_serviceState instanceof STOPPING) {
                    ((STOPPING) _serviceState).add(onCompleted);
                } else if (_serviceState == STOPPED) {
                    if (onCompleted != null) {
                        onCompleted.run();
                    }
                } else {
                    if (onCompleted != null) {
                        onCompleted.run();
                    }
                    error("stop should not be called from state: " + _serviceState);
                }
            }
        });
    }

    private void error(String msg) {
        try {
            throw new AssertionError(msg);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected State getServiceState() {
        return _serviceState;
    }

    abstract protected DispatchQueue getDispatchQueue();

    abstract protected void _start(Task onCompleted);

    abstract protected void _stop(Task onCompleted);

}