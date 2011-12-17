/**
 * Copyright (C) 2010-2011, FuseSource Corp.  All rights reserved.
 *
 *     http://fusesource.com
 *
 * The software in this package is published under the terms of the
 * CDDL license a copy of which has been included with this distribution
 * in the license.txt file.
 */
package org.fusesource.hawtdispatch.transport;

import org.fusesource.hawtdispatch.*;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

/**
 * An implementation of the {@link org.fusesource.hawtdispatch.transport.Transport} interface using raw tcp/ip
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public class TcpTransport extends ServiceBase implements Transport {

    abstract static class SocketState {
        void onStop(Runnable onCompleted) {
        }
        void onCanceled() {
        }
        boolean is(Class<? extends SocketState> clazz) {
            return getClass()==clazz;
        }
    }

    static class DISCONNECTED extends SocketState{}

    class CONNECTING extends SocketState{
        void onStop(Runnable onCompleted) {
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
        }

        void onStop(Runnable onCompleted) {
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
        Runnable createDisconnectTask() {
            return new Runnable(){
                public void run() {
                    listener.onTransportDisconnected();
                }
            };
        }
    }

    class CANCELING extends SocketState {
        private LinkedList<Runnable> runnables =  new LinkedList<Runnable>();
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
        void onStop(Runnable onCompleted) {
            trace("CANCELING.onCompleted");
            add(onCompleted);
            dispose = true;
        }
        void add(Runnable onCompleted) {
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
            for (Runnable runnable : runnables) {
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

        void onStop(Runnable onCompleted) {
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

    protected SocketChannel channel;

    protected SocketState socketState = new DISCONNECTED();

    protected DispatchQueue dispatchQueue;
    private DispatchSource readSource;
    private DispatchSource writeSource;
    private CustomDispatchSource<Integer, Integer> drainOutboundSource;
    private CustomDispatchSource<Integer, Integer> yieldSource;

    protected boolean useLocalHost = true;

    int maxReadRate;
    int maxWriteRate;
    int receiveBufferSize = 1024*64;
    int sendBufferSize = 1024*64;
    boolean keepAlive = true;


    public static final int IPTOS_LOWCOST = 0x02;
    public static final int IPTOS_RELIABILITY = 0x04;
    public static final int IPTOS_THROUGHPUT = 0x08;
    public static final int IPTOS_LOWDELAY = 0x10;

    int trafficClass = IPTOS_THROUGHPUT;

    protected RateLimitingChannel rateLimitingChannel;
    SocketAddress localAddress;
    SocketAddress remoteAddress;

    class RateLimitingChannel implements ReadableByteChannel, WritableByteChannel {

        int read_allowance = maxReadRate;
        boolean read_suspended = false;
        int read_resume_counter = 0;
        int write_allowance = maxWriteRate;
        boolean write_suspended = false;

        public void resetAllowance() {
            if( read_allowance != maxReadRate || write_allowance != maxWriteRate) {
                read_allowance = maxReadRate;
                write_allowance = maxWriteRate;
                if( write_suspended ) {
                    write_suspended = false;
                    resumeWrite();
                }
                if( read_suspended ) {
                    read_suspended = false;
                    resumeRead();
                    for( int i=0; i < read_resume_counter ; i++ ) {
                        resumeRead();
                    }
                }
            }
        }

        public int read(ByteBuffer dst) throws IOException {
            if( maxReadRate ==0 ) {
                return channel.read(dst);
            } else {
                int remaining = dst.remaining();
                if( read_allowance ==0 || remaining ==0 ) {
                    return 0;
                }

                int reduction = 0;
                if( remaining > read_allowance) {
                    reduction = remaining - read_allowance;
                    dst.limit(dst.limit() - reduction);
                }
                int rc=0;
                try {
                    rc = channel.read(dst);
                    read_allowance -= rc;
                } finally {
                    if( reduction!=0 ) {
                        if( dst.remaining() == 0 ) {
                            // we need to suspend the read now until we get
                            // a new allowance..
                            readSource.suspend();
                            read_suspended = true;
                        }
                        dst.limit(dst.limit() + reduction);
                    }
                }
                return rc;
            }
        }

        public int write(ByteBuffer src) throws IOException {
            if( maxWriteRate ==0 ) {
                return channel.write(src);
            } else {
                int remaining = src.remaining();
                if( write_allowance ==0 || remaining ==0 ) {
                    return 0;
                }

                int reduction = 0;
                if( remaining > write_allowance) {
                    reduction = remaining - write_allowance;
                    src.limit(src.limit() - reduction);
                }
                int rc = 0;
                try {
                    rc = channel.write(src);
                    write_allowance -= rc;
                } finally {
                    if( reduction!=0 ) {
                        if( src.remaining() == 0 ) {
                            // we need to suspend the read now until we get
                            // a new allowance..
                            write_suspended = true;
                            suspendWrite();
                        }
                        src.limit(src.limit() + reduction);
                    }
                }
                return rc;
            }
        }

        public boolean isOpen() {
            return channel.isOpen();
        }

        public void close() throws IOException {
            channel.close();
        }

        public void resumeRead() {
            if( read_suspended ) {
                read_resume_counter += 1;
            } else {
                _resumeRead();
            }
        }

    }

    private final Runnable CANCEL_HANDLER = new Runnable() {
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

    public void connected(SocketChannel channel) throws IOException, Exception {
        this.channel = channel;
        initializeChannel();
        this.socketState = new CONNECTED();
    }

    private void initializeChannel() throws Exception {
        this.channel.configureBlocking(false);
        Socket socket = channel.socket();
        try {
            socket.setReuseAddress(true);
        } catch (SocketException e) {
        }
        try {
            socket.setSoLinger(true, 0);
        } catch (SocketException e) {
        }
        try {
            socket.setTrafficClass(trafficClass);
        } catch (SocketException e) {
        }
        try {
            socket.setKeepAlive(keepAlive);
        } catch (SocketException e) {
        }
        try {
            socket.setTcpNoDelay(true);
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
        codec.setReadableByteChannel(readChannel());
        codec.setWritableByteChannel(writeChannel());
    }

    public void connecting(URI remoteLocation, URI localLocation) throws IOException, Exception {
        this.channel = SocketChannel.open();
        initializeChannel();
        this.remoteLocation = remoteLocation;
        this.localLocation = localLocation;

        if (localLocation != null) {
            InetSocketAddress localAddress = new InetSocketAddress(InetAddress.getByName(localLocation.getHost()), localLocation.getPort());
            channel.socket().bind(localAddress);
        }

        String host = resolveHostName(remoteLocation.getHost());
        InetSocketAddress remoteAddress = new InetSocketAddress(host, remoteLocation.getPort());
        channel.connect(remoteAddress);
        this.socketState = new CONNECTING();
    }


    public DispatchQueue getDispatchQueue() {
        return dispatchQueue;
    }

    public void setDispatchQueue(DispatchQueue queue) {
        this.dispatchQueue = queue;
    }

    public void _start(Runnable onCompleted) {
        try {
            if (socketState.is(CONNECTING.class) ) {
                trace("connecting...");
                // this allows the connect to complete..
                readSource = Dispatch.createSource(channel, SelectionKey.OP_CONNECT, dispatchQueue);
                readSource.setEventHandler(new Runnable() {
                    public void run() {
                        if (getServiceState() != STARTED) {
                            return;
                        }
                        try {
                            trace("connected.");
                            channel.finishConnect();
                            readSource.setCancelHandler(null);
                            readSource.cancel();
                            readSource=null;
                            socketState = new CONNECTED();
                            onConnected();
                        } catch (IOException e) {
                            onTransportFailure(e);
                        }
                    }
                });
                readSource.setCancelHandler(CANCEL_HANDLER);
                readSource.resume();

            } else if (socketState.is(CONNECTED.class) ) {
                dispatchQueue.execute(new Runnable() {
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

    public void _stop(final Runnable onCompleted) {
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
        yieldSource.setEventHandler(new Runnable() {
            public void run() {
                drainInbound();
            }
        });
        yieldSource.resume();
        drainOutboundSource = Dispatch.createSource(EventAggregators.INTEGER_ADD, dispatchQueue);
        drainOutboundSource.setEventHandler(new Runnable() {
            public void run() {
                flush();
            }
        });
        drainOutboundSource.resume();

        readSource = Dispatch.createSource(channel, SelectionKey.OP_READ, dispatchQueue);
        writeSource = Dispatch.createSource(channel, SelectionKey.OP_WRITE, dispatchQueue);

        readSource.setCancelHandler(CANCEL_HANDLER);
        writeSource.setCancelHandler(CANCEL_HANDLER);

        readSource.setEventHandler(new Runnable() {
            public void run() {
                drainInbound();
            }
        });
        writeSource.setEventHandler(new Runnable() {
            public void run() {
                flush();
            }
        });

        if( maxReadRate !=0 || maxWriteRate !=0 ) {
            rateLimitingChannel = new RateLimitingChannel();
            schedualRateAllowanceReset();
        }
        listener.onTransportConnected();
    }

    private void schedualRateAllowanceReset() {
        dispatchQueue.executeAfter(1, TimeUnit.SECONDS, new Runnable(){
            public void run() {
                if( !socketState.is(CONNECTED.class) ) {
                    return;
                }
                rateLimitingChannel.resetAllowance();
                schedualRateAllowanceReset();
            }
        });
    }

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

    protected void drainInbound() {
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

    public <T> T narrow(Class<T> target) {
        if (target.isAssignableFrom(getClass())) {
            return target.cast(this);
        }
        return null;
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
            if( rateLimitingChannel!=null ) {
                rateLimitingChannel.resumeRead();
            } else {
                _resumeRead();
            }
        }
    }

    private void _resumeRead() {
        readSource.resume();
        dispatchQueue.execute(new Runnable(){
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

    public String getTypeId() {
        return "tcp";
    }

    public void reconnect(URI uri) {
        throw new UnsupportedOperationException();
    }

    public TransportListener getTransportListener() {
        return listener;
    }

    public void setTransportListener(TransportListener listener) {
        this.listener = listener;
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

    public boolean isDisposed() {
        return getServiceState() == STOPPED;
    }

    public boolean isFaultTolerant() {
        return false;
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

    public SocketChannel getSocketChannel() {
        return channel;
    }

    public ReadableByteChannel readChannel() {
        if(rateLimitingChannel!=null) {
            return rateLimitingChannel;
        } else {
            return channel;
        }
    }

    public WritableByteChannel writeChannel() {
        if(rateLimitingChannel!=null) {
            return rateLimitingChannel;
        } else {
            return channel;
        }
    }

    public int getMaxReadRate() {
        return maxReadRate;
    }

    public void setMaxReadRate(int maxReadRate) {
        this.maxReadRate = maxReadRate;
    }

    public int getMaxWriteRate() {
        return maxWriteRate;
    }

    public void setMaxWriteRate(int maxWriteRate) {
        this.maxWriteRate = maxWriteRate;
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

    public boolean isKeepAlive() {
        return keepAlive;
    }

    public void setKeepAlive(boolean keepAlive) {
        this.keepAlive = keepAlive;
    }

}
