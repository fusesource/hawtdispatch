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
package org.fusesource.hawtdispatch.transport;

import org.fusesource.hawtdispatch.*;

import java.io.EOFException;
import java.io.IOException;
import java.net.SocketAddress;
import java.net.URI;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public class PipeTransport implements Transport {
    static private final Object EOF_TOKEN = new Object();

    final private PipeTransportServer server;
    PipeTransport peer;
    private TransportListener listener;
    private SocketAddress remoteAddress;
    private AtomicBoolean stopping = new AtomicBoolean();
    private String name;
    private boolean marshal;
    private boolean trace;

    private DispatchQueue dispatchQueue;
    private CustomDispatchSource<Object,LinkedList<Object>> dispatchSource;
    private boolean connected;

    private long writeCounter = 0;
    private long readCounter = 0;
    private ProtocolCodec protocolCodec;

    public PipeTransport(PipeTransportServer server) {
        this.server = server;
    }

    public DispatchQueue getDispatchQueue() {
        return dispatchQueue;
    }
    public void setDispatchQueue(DispatchQueue queue) {
        this.dispatchQueue = queue;
    }

    public void start(final Runnable onCompleted) {
        if (dispatchQueue == null) {
            throw new IllegalArgumentException("dispatchQueue is not set");
        }
        server.dispatchQueue.execute(new Runnable(){
            public void run() {
                dispatchSource = Dispatch.createSource(EventAggregators.linkedList(), dispatchQueue);
                dispatchSource.setEventHandler(new Runnable() {
                    public void run() {
                        try {
                            final LinkedList<Object> commands = dispatchSource.getData();
                            for (Object o : commands) {

                                if (o == EOF_TOKEN) {
                                    throw new EOFException();
                                }
                                readCounter++;
                                listener.onTransportCommand(o);
                            }

                            // let the peer know that they have been processed.
                            peer.dispatchQueue.execute(new Runnable() {
                                public void run() {
                                    outbound -= commands.size();
                                    drainInbound();
                                }
                            });
                        } catch (IOException e) {
                            listener.onTransportFailure(e);
                        }

                    }
                });
                if( peer.dispatchSource != null ) {
                    fireConnected();
                    peer.fireConnected();
                }
                if( onCompleted!=null ) {
                    onCompleted.run();
                }

            }
        });
    }

    private void fireConnected() {
        dispatchQueue.execute(new Runnable() {
            public void run() {
                connected = true;
                dispatchSource.resume();
                listener.onTransportConnected();
                drainInbound();
            }
        });
    }

    public void stop(Runnable onCompleted)  {
        if( connected ) {
            peer.dispatchSource.merge(EOF_TOKEN);
        }
        if( dispatchSource!=null ) {
            dispatchSource.setCancelHandler(onCompleted);
            dispatchSource.cancel();
        }
        setDispatchQueue(null);
    }

    static final class OneWay {
        final Object command;
        final Retained retained;

        public OneWay(Object command, Retained retained) {
            this.command = command;
            this.retained = retained;
        }
    }

    int outbound = 0;
    int maxOutbound = 100;

    public boolean full() {
        return outbound >= maxOutbound;
    }

    public boolean offer(Object command) {
        if( !connected ) {
            return false;
        }
        if( full() ) {
            return false;
        } else {
            transmit(command);
            return true;
        }
    }

    private void drainInbound() {
        if( !full() ) {
            listener.onRefill();
        }
    }

    private void transmit(Object command) {
        writeCounter++;
        outbound++;
        peer.dispatchSource.merge(command);
    }

    /**
     * @return The number of objects sent by the transport.
     */
    public long getWriteCounter() {
        return writeCounter;
    }

    /**
     * @return The number of objects received by the transport.
     */
    public long getReadCounter() {
        return readCounter;
    }

    public SocketAddress getLocalAddress() {
        return remoteAddress;
    }

    public SocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    public <T> T narrow(Class<T> target) {
        if (target.isAssignableFrom(getClass())) {
            return target.cast(this);
        }
        return null;
    }

    public void suspendRead() {
        dispatchSource.suspend();
    }

    public void resumeRead() {
        dispatchSource.resume();
    }
    public void reconnect(URI uri) {
        throw new UnsupportedOperationException();
    }

    public String getTypeId() {
        return "pipe";
    }

    public void setRemoteAddress(final String remoteAddress) {
        this.remoteAddress = new SocketAddress() {
            @Override
            public String toString() {
                return remoteAddress;
            }
        };
        if (name == null) {
            name = remoteAddress;
        }
    }

    public void setName(String name) {
        this.name = name;
    }

    public TransportListener getTransportListener() {
        return listener;
    }
    public void setTransportListener(TransportListener listener) {
        this.listener = listener;
    }

    public ProtocolCodec getProtocolCodec() {
        return protocolCodec;
    }
    public void setProtocolCodec(ProtocolCodec protocolCodec) {
        this.protocolCodec = protocolCodec;
    }


    public boolean isTrace() {
        return trace;
    }

    public void setTrace(boolean trace) {
        this.trace = trace;
    }

    public boolean isMarshal() {
        return marshal;
    }
    public void setMarshal(boolean marshall) {
        this.marshal = marshall;
    }

    public boolean isConnected() {
        return !stopping.get();
    }
    public boolean isDisposed() {
        return false;
    }
    public boolean isFaultTolerant() {
        return false;
    }
}
