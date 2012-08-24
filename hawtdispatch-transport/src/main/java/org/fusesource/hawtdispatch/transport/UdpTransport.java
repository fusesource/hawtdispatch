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

import org.fusesource.hawtdispatch.*;

import java.io.IOException;
import java.net.*;
import java.nio.channels.DatagramChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.WritableByteChannel;
import java.util.LinkedList;
import java.util.concurrent.Executor;

/**
 * <p>
 * </p>
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public class UdpTransport extends ServiceBase implements Transport {

    public static final SocketAddress ANY_ADDRESS = new SocketAddress() {
        @Override
        public String toString() {
            return "*:*";
        }
    };


    abstract static class SocketState {
        void onStop(Task onCompleted) {
        }
        void onCanceled() {
        }
        boolean is(Class<? extends SocketState> clazz) {
            return getClass()==clazz;
        }
    }

    static class DISCONNECTED extends SocketState{}

    class CONNECTING extends SocketState{
        void onStop(Task onCompleted) {
            trace("CONNECTING.onStop");
            CANCELING state = new CANCELING();
            socketState = state;
            state.onStop(onCompleted);
        }
        void onCanceled() {
            trace("CONNECTING.onCanceled");
            CANCELING state = new CANCELING();
            socketState = state;
            state.onCanceled();
        }
    }

    class CONNECTED extends SocketState {
        public CONNECTED() {
            localAddress = channel.socket().getLocalSocketAddress();
            remoteAddress = channel.socket().getRemoteSocketAddress();
            if(remoteAddress == null ) {
                remoteAddress = ANY_ADDRESS;
            }
        }

        void onStop(Task onCompleted) {
            trace("CONNECTED.onStop");
            CANCELING state = new CANCELING();
            socketState = state;
            state.add(createDisconnectTask());
            state.onStop(onCompleted);
        }
        void onCanceled() {
            trace("CONNECTED.onCanceled");
            CANCELING state = new CANCELING();
            socketState = state;
            state.add(createDisconnectTask());
            state.onCanceled();
        }
        Task createDisconnectTask() {
            return new Task(){
                public void run() {
                    listener.onTransportDisconnected();
                }
            };
        }
    }

    class CANCELING extends SocketState {
        private LinkedList<Task> runnables =  new LinkedList<Task>();
        private int remaining;
        private boolean dispose;

        public CANCELING() {
            if( readSource!=null ) {
                remaining++;
                readSource.cancel();
            }
            if( writeSource!=null ) {
                remaining++;
                writeSource.cancel();
            }
        }
        void onStop(Task onCompleted) {
            trace("CANCELING.onCompleted");
            add(onCompleted);
            dispose = true;
        }
        void add(Task onCompleted) {
            if( onCompleted!=null ) {
                runnables.add(onCompleted);
            }
        }
        void onCanceled() {
            trace("CANCELING.onCanceled");
            remaining--;
            if( remaining!=0 ) {
                return;
            }
            try {
                channel.close();
            } catch (IOException ignore) {
            }
            socketState = new CANCELED(dispose);
            for (Task runnable : runnables) {
                runnable.run();
            }
            if (dispose) {
                dispose();
            }
        }
    }

    class CANCELED extends SocketState {
        private boolean disposed;

        public CANCELED(boolean disposed) {
            this.disposed=disposed;
        }

        void onStop(Task onCompleted) {
            trace("CANCELED.onStop");
            if( !disposed ) {
                disposed = true;
                dispose();
            }
            onCompleted.run();
        }
    }

    protected URI remoteLocation;
    protected URI localLocation;
    protected TransportListener listener;
    protected ProtocolCodec codec;

    protected DatagramChannel channel;

    protected SocketState socketState = new DISCONNECTED();

    protected DispatchQueue dispatchQueue;
    private DispatchSource readSource;
    private DispatchSource writeSource;
    protected CustomDispatchSource<Integer, Integer> drainOutboundSource;
    protected CustomDispatchSource<Integer, Integer> yieldSource;

    protected boolean useLocalHost = true;

    int receiveBufferSize = 1024*64;
    int sendBufferSize = 1024*64;


    public static final int IPTOS_LOWCOST = 0x02;
    public static final int IPTOS_RELIABILITY = 0x04;
    public static final int IPTOS_THROUGHPUT = 0x08;
    public static final int IPTOS_LOWDELAY = 0x10;

    int trafficClass = IPTOS_THROUGHPUT;

    SocketAddress localAddress;
    SocketAddress remoteAddress = ANY_ADDRESS;
    Executor blockingExecutor;

    private final Task CANCEL_HANDLER = new Task() {
        public void run() {
            socketState.onCanceled();
        }
    };

    static final class OneWay {
        final Object command;
        final Retained retained;

        public OneWay(Object command, Retained retained) {
            this.command = command;
            this.retained = retained;
        }
    }

    public void connected(DatagramChannel channel) throws IOException, Exception {
        this.channel = channel;
        initializeChannel();
        this.socketState = new CONNECTED();
    }

    protected void initializeChannel() throws Exception {
        this.channel.configureBlocking(false);
        DatagramSocket socket = channel.socket();
        try {
            socket.setReuseAddress(true);
        } catch (SocketException e) {
        }
        try {
            socket.setTrafficClass(trafficClass);
        } catch (SocketException e) {
        }
        try {
            socket.setReceiveBufferSize(receiveBufferSize);
        } catch (SocketException e) {
        }
        try {
            socket.setSendBufferSize(sendBufferSize);
        } catch (SocketException e) {
        }
        if( channel!=null && codec!=null ) {
            initializeCodec();
        }
    }

    protected void initializeCodec() throws Exception {
        codec.setTransport(this);
    }

    public void connecting(final URI remoteLocation, final URI localLocation) throws Exception {
        this.channel = DatagramChannel.open();
        initializeChannel();
        this.remoteLocation = remoteLocation;
        this.localLocation = localLocation;
        socketState = new CONNECTING();
    }


    public DispatchQueue getDispatchQueue() {
        return dispatchQueue;
    }

    public void setDispatchQueue(DispatchQueue queue) {
        this.dispatchQueue = queue;
        if(readSource!=null) readSource.setTargetQueue(queue);
        if(writeSource!=null) writeSource.setTargetQueue(queue);
        if(drainOutboundSource!=null) drainOutboundSource.setTargetQueue(queue);
        if(yieldSource!=null) yieldSource.setTargetQueue(queue);
    }

    public void _start(Task onCompleted) {
        try {
            if ( socketState.is(CONNECTING.class) ) {
                // Resolving host names might block.. so do it on the blocking executor.
                this.blockingExecutor.execute(new Runnable() {
                    public void run() {
                        // No need to complete if we have been canceled.
                        if( ! socketState.is(CONNECTING.class) ) {
                            return;
                        }
                        try {

                            final InetSocketAddress localAddress = (localLocation != null) ?
                                 new InetSocketAddress(InetAddress.getByName(localLocation.getHost()), localLocation.getPort())
                                 : null;

                            String host = resolveHostName(remoteLocation.getHost());
                            final InetSocketAddress remoteAddress = new InetSocketAddress(host, remoteLocation.getPort());

                            // Done resolving.. switch back to the dispatch queue.
                            dispatchQueue.execute(new Task() {
                                @Override
                                public void run() {
                                    try {
                                        if(localAddress!=null) {
                                            channel.socket().bind(localAddress);
                                        }
                                        channel.connect(remoteAddress);
                                    } catch (IOException e) {
                                        try {
                                            channel.close();
                                        } catch (IOException ignore) {
                                        }
                                        socketState = new CANCELED(true);
                                        listener.onTransportFailure(e);
                                    }
                                }
                            });

                        } catch (IOException e) {
                            try {
                                channel.close();
                            } catch (IOException ignore) {
                            }
                            socketState = new CANCELED(true);
                            listener.onTransportFailure(e);
                        }
                    }
                });

            } else if (socketState.is(CONNECTED.class) ) {
                dispatchQueue.execute(new Task() {
                    public void run() {
                        try {
                            trace("was connected.");
                            onConnected();
                        } catch (IOException e) {
                             onTransportFailure(e);
                        }
                    }
                });
            } else {
                System.err.println("cannot be started.  socket state is: "+socketState);
            }
        } finally {
            if( onCompleted!=null ) {
                onCompleted.run();
            }
        }
    }

    public void _stop(final Task onCompleted) {
        trace("stopping.. at state: "+socketState);
        socketState.onStop(onCompleted);
    }

    protected String resolveHostName(String host) throws UnknownHostException {
        String localName = InetAddress.getLocalHost().getHostName();
        if (localName != null && isUseLocalHost()) {
            if (localName.equals(host)) {
                return "localhost";
            }
        }
        return host;
    }

    protected void onConnected() throws IOException {
        yieldSource = Dispatch.createSource(EventAggregators.INTEGER_ADD, dispatchQueue);
        yieldSource.setEventHandler(new Task() {
            public void run() {
                drainInbound();
            }
        });
        yieldSource.resume();
        drainOutboundSource = Dispatch.createSource(EventAggregators.INTEGER_ADD, dispatchQueue);
        drainOutboundSource.setEventHandler(new Task() {
            public void run() {
                flush();
            }
        });
        drainOutboundSource.resume();

        readSource = Dispatch.createSource(channel, SelectionKey.OP_READ, dispatchQueue);
        writeSource = Dispatch.createSource(channel, SelectionKey.OP_WRITE, dispatchQueue);

        readSource.setCancelHandler(CANCEL_HANDLER);
        writeSource.setCancelHandler(CANCEL_HANDLER);

        readSource.setEventHandler(new Task() {
            public void run() {
                drainInbound();
            }
        });
        writeSource.setEventHandler(new Task() {
            public void run() {
                flush();
            }
        });
        listener.onTransportConnected();
    }

    Task onDispose;

    private void dispose() {
        if( readSource!=null ) {
            readSource.cancel();
            readSource=null;
        }

        if( writeSource!=null ) {
            writeSource.cancel();
            writeSource=null;
        }
        this.codec = null;
        if(onDispose!=null) {
            onDispose.run();
            onDispose = null;
        }
    }

    public void onTransportFailure(IOException error) {
        listener.onTransportFailure(error);
        socketState.onCanceled();
    }


    public boolean full() {
        return codec==null || codec.full();
    }

    boolean rejectingOffers;

    public boolean offer(Object command) {
        dispatchQueue.assertExecuting();
        try {
            if (!socketState.is(CONNECTED.class)) {
                throw new IOException("Not connected.");
            }
            if (getServiceState() != STARTED) {
                throw new IOException("Not running.");
            }

            ProtocolCodec.BufferState rc = codec.write(command);
            rejectingOffers = codec.full();
            switch (rc ) {
                case FULL:
                    return false;
                default:
                    drainOutboundSource.merge(1);
                    return true;
            }
        } catch (IOException e) {
            onTransportFailure(e);
            return false;
        }

    }

    boolean writeResumedForCodecFlush = false;

    /**
     *
     */
    public void flush() {
        dispatchQueue.assertExecuting();
        if (getServiceState() != STARTED || !socketState.is(CONNECTED.class)) {
            return;
        }
        try {
            if( codec.flush() == ProtocolCodec.BufferState.EMPTY && transportFlush() ) {
                if( writeResumedForCodecFlush) {
                    writeResumedForCodecFlush = false;
                    suspendWrite();
                }
                rejectingOffers = false;
                listener.onRefill();

            } else {
                if(!writeResumedForCodecFlush) {
                    writeResumedForCodecFlush = true;
                    resumeWrite();
                }
            }
        } catch (IOException e) {
            onTransportFailure(e);
        }
    }

    protected boolean transportFlush() throws IOException {
        return true;
    }

    public void drainInbound() {
        if (!getServiceState().isStarted() || readSource.isSuspended()) {
            return;
        }
        try {
            long initial = codec.getReadCounter();
            // Only process upto 2 x the read buffer worth of data at a time so we can give
            // other connections a chance to process their requests.
            while( codec.getReadCounter()-initial < codec.getReadBufferSize()<<2 ) {
                Object command = codec.read();
                if ( command!=null ) {
                    try {
                        listener.onTransportCommand(command);
                    } catch (Throwable e) {
                        e.printStackTrace();
                        onTransportFailure(new IOException("Transport listener failure."));
                    }

                    // the transport may be suspended after processing a command.
                    if (getServiceState() == STOPPED || readSource.isSuspended()) {
                        return;
                    }
                } else {
                    return;
                }
            }
            yieldSource.merge(1);
        } catch (IOException e) {
            onTransportFailure(e);
        }
    }

    public SocketAddress getLocalAddress() {
        return localAddress;
    }

    public SocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    private boolean assertConnected() {
        try {
            if ( !isConnected() ) {
                throw new IOException("Not connected.");
            }
            return true;
        } catch (IOException e) {
            onTransportFailure(e);
        }
        return false;
    }

    public void suspendRead() {
        if( isConnected() && readSource!=null ) {
            readSource.suspend();
        }
    }


    public void resumeRead() {
        if( isConnected() && readSource!=null ) {
            _resumeRead();
        }
    }

    private void _resumeRead() {
        readSource.resume();
        dispatchQueue.execute(new Task(){
            public void run() {
                drainInbound();
            }
        });
    }

    protected void suspendWrite() {
        if( isConnected() && writeSource!=null ) {
            writeSource.suspend();
        }
    }

    protected void resumeWrite() {
        if( isConnected() && writeSource!=null ) {
            writeSource.resume();
        }
    }

    public TransportListener getTransportListener() {
        return listener;
    }

    public void setTransportListener(TransportListener transportListener) {
        this.listener = transportListener;
    }

    public ProtocolCodec getProtocolCodec() {
        return codec;
    }

    public void setProtocolCodec(ProtocolCodec protocolCodec) throws Exception {
        this.codec = protocolCodec;
        if( channel!=null && codec!=null ) {
            initializeCodec();
        }
    }

    public boolean isConnected() {
        return socketState.is(CONNECTED.class);
    }

    public boolean isClosed() {
        return getServiceState() == STOPPED;
    }

    public boolean isUseLocalHost() {
        return useLocalHost;
    }

    /**
     * Sets whether 'localhost' or the actual local host name should be used to
     * make local connections. On some operating systems such as Macs its not
     * possible to connect as the local host name so localhost is better.
     */
    public void setUseLocalHost(boolean useLocalHost) {
        this.useLocalHost = useLocalHost;
    }

    private void trace(String message) {
        // TODO:
    }

    public DatagramChannel getDatagramChannel() {
        return channel;
    }

    public ReadableByteChannel getReadChannel() {
        return channel;
    }

    public WritableByteChannel getWriteChannel() {
        return channel;
    }

    public int getTrafficClass() {
        return trafficClass;
    }

    public void setTrafficClass(int trafficClass) {
        this.trafficClass = trafficClass;
    }

    public int getReceiveBufferSize() {
        return receiveBufferSize;
    }

    public void setReceiveBufferSize(int receiveBufferSize) {
        this.receiveBufferSize = receiveBufferSize;
    }

    public int getSendBufferSize() {
        return sendBufferSize;
    }

    public void setSendBufferSize(int sendBufferSize) {
        this.sendBufferSize = sendBufferSize;
    }

    public Executor getBlockingExecutor() {
        return blockingExecutor;
    }

    public void setBlockingExecutor(Executor blockingExecutor) {
        this.blockingExecutor = blockingExecutor;
    }

}
