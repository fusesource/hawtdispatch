/**
 * Copyright (C) 2010-2011, FuseSource Corp.  All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fusesource.hawtdispatch.transport;

import org.fusesource.hawtdispatch.Dispatch;

import java.util.concurrent.TimeUnit;

/**
 * <p>A HeartBeatMonitor can be used to watch the read and write
 * activity of a transport and raise events when the write side
 * or read side has been idle too long.</p>
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public class HeartBeatMonitor {

    Transport transport;
    long initialWriteCheckDelay;
    long initialReadCheckDelay;
    long writeInterval;
    long readInterval;

    Runnable onKeepAlive = Dispatch.NOOP;
    Runnable onDead = Dispatch.NOOP;

    short session = 0;

    boolean readSuspendedInterval;
    short readSuspendCount;

    public void suspendRead() {
        readSuspendCount++;
        readSuspendedInterval = true;
    }

    public void resumeRead() {
        readSuspendCount--;
    }

    private void schedule(final short session, long interval, final Runnable func) {
        if (this.session == session) {
            transport.getDispatchQueue().executeAfter(interval, TimeUnit.MILLISECONDS, new Runnable() {
                public void run() {
                    if (HeartBeatMonitor.this.session == session) {
                        func.run();
                    }
                }
            });
        }
    }

    private void scheduleCheckWrites(final short session) {
        final ProtocolCodec codec = transport.getProtocolCodec();
        Runnable func;
        if (codec == null) {
            func = new Runnable() {
                public void run() {
                    scheduleCheckWrites(session);
                }
            };
        } else {
            final long lastWriteCounter = codec.getWriteCounter();
            func = new Runnable() {
                public void run() {
                    if (lastWriteCounter == codec.getWriteCounter()) {
                        onKeepAlive.run();
                    }
                    scheduleCheckWrites(session);
                }
            };
        }
        schedule(session, writeInterval / 2, func);
    }

    private void scheduleCheckReads(final short session) {
        final ProtocolCodec codec = transport.getProtocolCodec();
        Runnable func;
        if (codec == null) {
            func = new Runnable() {
                public void run() {
                    scheduleCheckReads(session);
                }
            };
        } else {
            final long lastReadCounter = codec.getReadCounter();
            func = new Runnable() {
                public void run() {
                    if (lastReadCounter == codec.getReadCounter() && !readSuspendedInterval && readSuspendCount == 0) {
                        onDead.run();
                    }
                    readSuspendedInterval = false;
                    scheduleCheckReads(session);
                }
            };
        }
        schedule(session, readInterval, func);
    }

    public void start() {
        session++;
        readSuspendedInterval = false;
        if (writeInterval != 0) {
            if (initialWriteCheckDelay != 0) {
                transport.getDispatchQueue().executeAfter(initialWriteCheckDelay, TimeUnit.MILLISECONDS, new Runnable() {
                    public void run() {
                        scheduleCheckWrites(session);
                    }
                });
            } else {
                scheduleCheckWrites(session);
            }
        }
        if (readInterval != 0) {
            if (initialReadCheckDelay != 0) {
                transport.getDispatchQueue().executeAfter(initialReadCheckDelay, TimeUnit.MILLISECONDS, new Runnable() {
                    public void run() {
                        scheduleCheckReads(session);
                    }
                });
            } else {
                scheduleCheckReads(session);
            }
        }
    }

    public void stop() {
        session++;
    }


    public long getInitialReadCheckDelay() {
        return initialReadCheckDelay;
    }

    public void setInitialReadCheckDelay(long initialReadCheckDelay) {
        this.initialReadCheckDelay = initialReadCheckDelay;
    }

    public long getInitialWriteCheckDelay() {
        return initialWriteCheckDelay;
    }

    public void setInitialWriteCheckDelay(long initialWriteCheckDelay) {
        this.initialWriteCheckDelay = initialWriteCheckDelay;
    }

    public Runnable getOnDead() {
        return onDead;
    }

    public void setOnDead(Runnable onDead) {
        this.onDead = onDead;
    }

    public Runnable getOnKeepAlive() {
        return onKeepAlive;
    }

    public void setOnKeepAlive(Runnable onKeepAlive) {
        this.onKeepAlive = onKeepAlive;
    }

    public long getWriteInterval() {
        return writeInterval;
    }

    public void setWriteInterval(long writeInterval) {
        this.writeInterval = writeInterval;
    }

    public Transport getTransport() {
        return transport;
    }

    public void setTransport(Transport transport) {
        this.transport = transport;
    }

    public long getReadInterval() {
        return readInterval;
    }

    public void setReadInterval(long readInterval) {
        this.readInterval = readInterval;
    }
}
