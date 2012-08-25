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

import org.fusesource.hawtdispatch.Task;

import javax.net.ssl.*;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.channels.WritableByteChannel;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;

import static javax.net.ssl.SSLEngineResult.HandshakeStatus.*;
import static javax.net.ssl.SSLEngineResult.Status.BUFFER_OVERFLOW;

/**
 * Implements the SSL protocol as a WrappingProtocolCodec.  Useful for when
 * you want to switch to the SSL protocol on a regular TCP Transport.
 */
public class SslProtocolCodec implements WrappingProtocolCodec, SecuredSession {

    private ReadableByteChannel readChannel;
    private WritableByteChannel writeChannel;

    public enum ClientAuth {
        WANT, NEED, NONE
    };

    private SSLContext sslContext;
    private SSLEngine engine;

    private ByteBuffer readBuffer;
    private boolean readUnderflow;

    private ByteBuffer writeBuffer;
    private boolean writeFlushing;

    private ByteBuffer readOverflowBuffer;
    Transport transport;

    int lastReadSize;
    int lastWriteSize;
    long readCounter;
    long writeCounter;

    ProtocolCodec next;


    public SslProtocolCodec() {
    }

    public ProtocolCodec getNext() {
        return next;
    }
    public void setNext(ProtocolCodec next) {
        this.next = next;
        initNext();
    }

    private void initNext() {
        if( next!=null ) {
            this.next.setTransport(new TransportFilter(transport){
                public ReadableByteChannel getReadChannel() {
                    return sslReadChannel;
                }
                public WritableByteChannel getWriteChannel() {
                    return sslWriteChannel;
                }
            });
        }
    }

    public void setSSLContext(SSLContext ctx) {
        assert engine == null;
        this.sslContext = ctx;
    }

    public SslProtocolCodec client() throws Exception {
        initializeEngine();
        engine.setUseClientMode(true);
        engine.beginHandshake();
        return this;
    }

    public SslProtocolCodec server(ClientAuth clientAuth) throws Exception {
        initializeEngine();
        engine.setUseClientMode(false);
        switch (clientAuth) {
            case WANT: engine.setWantClientAuth(true); break;
            case NEED: engine.setNeedClientAuth(true); break;
            case NONE: engine.setWantClientAuth(false); break;
        }
        engine.beginHandshake();
        return this;
    }

    protected void initializeEngine() throws Exception {
        assert engine == null;
        if( sslContext == null ) {
            sslContext = SSLContext.getDefault();
        }
        engine = sslContext.createSSLEngine();
        SSLSession session = engine.getSession();
        readBuffer = ByteBuffer.allocateDirect(session.getPacketBufferSize());
        readBuffer.flip();
        writeBuffer = ByteBuffer.allocateDirect(session.getPacketBufferSize());
    }


    public SSLSession getSSLSession() {
        return engine==null ? null : engine.getSession();
    }

    public X509Certificate[] getPeerX509Certificates() {
    	if( engine==null ) {
            return null;
        }
        try {
            ArrayList<X509Certificate> rc = new ArrayList<X509Certificate>();
            for( Certificate c:engine.getSession().getPeerCertificates() ) {
                if(c instanceof X509Certificate) {
                    rc.add((X509Certificate) c);
                }
            }
            return rc.toArray(new X509Certificate[rc.size()]);
        } catch (SSLPeerUnverifiedException e) {
            return null;
        }
    }

    SSLReadChannel sslReadChannel = new SSLReadChannel();
    SSLWriteChannel sslWriteChannel = new SSLWriteChannel();

    public void setTransport(Transport transport) {
        this.transport = transport;
        this.readChannel = transport.getReadChannel();
        this.writeChannel = transport.getWriteChannel();
        initNext();
    }

    public void handshake() throws IOException {
        if( !transportFlush() ) {
            return;
        }
        switch (engine.getHandshakeStatus()) {
            case NEED_TASK:
                final Runnable task = engine.getDelegatedTask();
                if( task!=null ) {
                    transport.getBlockingExecutor().execute(new Task() {
                        public void run() {
                            task.run();
                            transport.getDispatchQueue().execute(new Task() {
                                public void run() {
                                    if (readChannel.isOpen() && writeChannel.isOpen()) {
                                        try {
                                            handshake();
                                        } catch (IOException e) {
                                            transport.getTransportListener().onTransportFailure(e);
                                        }
                                    }
                                }
                            });
                        }
                    });
                }
                break;

            case NEED_WRAP:
                secure_write(ByteBuffer.allocate(0));
                break;

            case NEED_UNWRAP:
                if( secure_read(ByteBuffer.allocate(0)) == -1) {
                    throw new EOFException("Peer disconnected during ssl handshake");
                }
                break;

            case FINISHED:
            case NOT_HANDSHAKING:
                transport.drainInbound();
                transport.getTransportListener().onRefill();
                break;

            default:
                System.err.println("Unexpected ssl engine handshake status: "+ engine.getHandshakeStatus());
                break;
        }
    }

    /**
     * @return true if fully flushed.
     * @throws IOException
     */
    protected boolean transportFlush() throws IOException {
        while (true) {
            if(writeFlushing) {
                lastWriteSize = writeChannel.write(writeBuffer);
                if( lastWriteSize > 0 ) {
                    writeCounter += lastWriteSize;
                }
                if( !writeBuffer.hasRemaining() ) {
                    writeBuffer.clear();
                    writeFlushing = false;
                    return true;
                } else {
                    return false;
                }
            } else {
                if( writeBuffer.position()!=0 ) {
                    writeBuffer.flip();
                    writeFlushing = true;
                } else {
                    return true;
                }
            }
        }
    }

    private int secure_read(ByteBuffer plain) throws IOException {
        int rc=0;
        while ( plain.hasRemaining() ^ engine.getHandshakeStatus() == NEED_UNWRAP ) {
            if( readOverflowBuffer !=null ) {
                if(  plain.hasRemaining() ) {
                    // lets drain the overflow buffer before trying to suck down anymore
                    // network bytes.
                    int size = Math.min(plain.remaining(), readOverflowBuffer.remaining());
                    plain.put(readOverflowBuffer.array(), readOverflowBuffer.position(), size);
                    readOverflowBuffer.position(readOverflowBuffer.position()+size);
                    if( !readOverflowBuffer.hasRemaining() ) {
                        readOverflowBuffer = null;
                    }
                    rc += size;
                } else {
                    return rc;
                }
            } else if( readUnderflow ) {
                lastReadSize = readChannel.read(readBuffer);
                if( lastReadSize == -1 ) {  // peer closed socket.
                    if (rc==0) {
                        return -1;
                    } else {
                        return rc;
                    }
                }
                if( lastReadSize==0 ) {  // no data available right now.
                    return rc;
                }
                readCounter += lastReadSize;
                // read in some more data, perhaps now we can unwrap.
                readUnderflow = false;
                readBuffer.flip();
            } else {
                SSLEngineResult result = engine.unwrap(readBuffer, plain);
                rc += result.bytesProduced();
                if( result.getStatus() == BUFFER_OVERFLOW ) {
                    readOverflowBuffer = ByteBuffer.allocate(engine.getSession().getApplicationBufferSize());
                    result = engine.unwrap(readBuffer, readOverflowBuffer);
                    if( readOverflowBuffer.position()==0 ) {
                        readOverflowBuffer = null;
                    } else {
                        readOverflowBuffer.flip();
                    }
                }
                switch( result.getStatus() ) {
                    case CLOSED:
                        if (rc==0) {
                            engine.closeInbound();
                            return -1;
                        } else {
                            return rc;
                        }
                    case OK:
                        if ( engine.getHandshakeStatus()!=NOT_HANDSHAKING ) {
                            handshake();
                        }
                        break;
                    case BUFFER_UNDERFLOW:
                        readBuffer.compact();
                        readUnderflow = true;
                        break;
                    case BUFFER_OVERFLOW:
                        throw new AssertionError("Unexpected case.");
                }
            }
        }
        return rc;
    }

    private int secure_write(ByteBuffer plain) throws IOException {
        if( !transportFlush() ) {
            // can't write anymore until the write_secured_buffer gets fully flushed out..
            return 0;
        }
        int rc = 0;
        while ( plain.hasRemaining() ^ engine.getHandshakeStatus()==NEED_WRAP ) {
            SSLEngineResult result = engine.wrap(plain, writeBuffer);
            assert result.getStatus()!= BUFFER_OVERFLOW;
            rc += result.bytesConsumed();
            if( !transportFlush() ) {
                break;
            }
        }
        if( plain.remaining()==0 && engine.getHandshakeStatus()!=NOT_HANDSHAKING ) {
            handshake();
        }
        return rc;
    }

    public class SSLReadChannel implements ScatteringByteChannel {

        public int read(ByteBuffer plain) throws IOException {
            if ( engine.getHandshakeStatus()!=NOT_HANDSHAKING ) {
                handshake();
            }
            return secure_read(plain);
        }

        public boolean isOpen() {
            return readChannel.isOpen();
        }

        public void close() throws IOException {
            readChannel.close();
        }

        public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
            if(offset+length > dsts.length || length<0 || offset<0) {
                throw new IndexOutOfBoundsException();
            }
            long rc=0;
            for (int i = 0; i < length; i++) {
                ByteBuffer dst = dsts[offset+i];
                if(dst.hasRemaining()) {
                    rc += read(dst);
                }
                if( dst.hasRemaining() ) {
                    return rc;
                }
            }
            return rc;
        }

        public long read(ByteBuffer[] dsts) throws IOException {
            return read(dsts, 0, dsts.length);
        }
    }

    public class SSLWriteChannel implements GatheringByteChannel {

        public int write(ByteBuffer plain) throws IOException {
            if ( engine.getHandshakeStatus()!=NOT_HANDSHAKING ) {
                handshake();
            }
            return secure_write(plain);
        }

        public boolean isOpen() {
            return writeChannel.isOpen();
        }

        public void close() throws IOException {
            writeChannel.close();
        }

        public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
            if(offset+length > srcs.length || length<0 || offset<0) {
                throw new IndexOutOfBoundsException();
            }
            long rc=0;
            for (int i = 0; i < length; i++) {
                ByteBuffer src = srcs[offset+i];
                if(src.hasRemaining()) {
                    rc += write(src);
                }
                if( src.hasRemaining() ) {
                    return rc;
                }
            }
            return rc;
        }

        public long write(ByteBuffer[] srcs) throws IOException {
            return write(srcs, 0, srcs.length);
        }
    }

    public void unread(byte[] buffer) {
        readBuffer.compact();
        if( readBuffer.remaining() < buffer.length) {
            throw new IllegalStateException("Cannot unread now");
        }
        readBuffer.put(buffer);
        readBuffer.flip();
    }

    public Object read() throws IOException {
        return next.read();
    }

    public ProtocolCodec.BufferState write(Object value) throws IOException {
        return next.write(value);
    }

    public ProtocolCodec.BufferState flush() throws IOException {
        return next.flush();
    }

    public boolean full() {
        return next.full();
    }

    public long getWriteCounter() {
        return writeCounter;
    }

    public long getLastWriteSize() {
        return lastWriteSize;
    }

    public long getReadCounter() {
        return readCounter;
    }

    public long getLastReadSize() {
        return lastReadSize;
    }

    public int getReadBufferSize() {
        return readBuffer.capacity();
    }

    public int getWriteBufferSize() {
        return writeBuffer.capacity();
    }



}