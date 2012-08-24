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

import org.fusesource.hawtbuf.Buffer;
import org.fusesource.hawtbuf.DataByteArrayOutputStream;
import org.fusesource.hawtdispatch.util.BufferPool;
import org.fusesource.hawtdispatch.util.BufferPools;

import java.io.EOFException;
import java.io.IOException;
import java.net.ProtocolException;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.LinkedList;

/**
 * Provides an abstract base class to make implementing the ProtocolCodec interface
 * easier.
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public abstract class AbstractProtocolCodec implements ProtocolCodec {

    protected BufferPools bufferPools;
    protected BufferPool writeBufferPool;
    protected BufferPool readBufferPool;

    protected int writeBufferSize = 1024 * 64;
    protected long writeCounter = 0L;
    protected GatheringByteChannel writeChannel = null;
    protected DataByteArrayOutputStream nextWriteBuffer;
    protected long lastWriteIoSize = 0;

    protected LinkedList<ByteBuffer> writeBuffer = new LinkedList<ByteBuffer>();
    private long writeBufferRemaining = 0;


    public static interface Action {
        Object apply() throws IOException;
    }

    protected long readCounter = 0L;
    protected int readBufferSize = 1024 * 64;
    protected ReadableByteChannel readChannel = null;
    protected ByteBuffer readBuffer;
    protected ByteBuffer directReadBuffer = null;

    protected int readEnd;
    protected int readStart;
    protected int lastReadIoSize;
    protected Action nextDecodeAction;

    public void setTransport(Transport transport) {
        this.writeChannel = (GatheringByteChannel) transport.getWriteChannel();
        this.readChannel = transport.getReadChannel();
        if( nextDecodeAction==null ) {
            nextDecodeAction = initialDecodeAction();
        }
        if( transport instanceof TcpTransport) {
            TcpTransport tcp = (TcpTransport) transport;
            writeBufferSize = tcp.getSendBufferSize();
            readBufferSize = tcp.getReceiveBufferSize();
        } else if( transport instanceof UdpTransport) {
            UdpTransport tcp = (UdpTransport) transport;
            writeBufferSize = tcp.getSendBufferSize();
            readBufferSize = tcp.getReceiveBufferSize();
        } else {
            try {
                if (this.writeChannel instanceof SocketChannel) {
                    writeBufferSize = ((SocketChannel) this.writeChannel).socket().getSendBufferSize();
                    readBufferSize = ((SocketChannel) this.readChannel).socket().getReceiveBufferSize();
                } else if (this.writeChannel instanceof SslTransport.SSLChannel) {
                    writeBufferSize = ((SslTransport.SSLChannel) this.readChannel).socket().getSendBufferSize();
                    readBufferSize = ((SslTransport.SSLChannel) this.writeChannel).socket().getReceiveBufferSize();
                }
            } catch (SocketException ignore) {
            }
        }
        if( bufferPools!=null ) {
            readBufferPool = bufferPools.getBufferPool(readBufferSize);
            writeBufferPool = bufferPools.getBufferPool(writeBufferSize);
        }
    }

    public int getReadBufferSize() {
        return readBufferSize;
    }

    public int getWriteBufferSize() {
        return writeBufferSize;
    }

    public boolean full() {
        return writeBufferRemaining >= writeBufferSize;
    }

    public boolean isEmpty() {
        return writeBufferRemaining == 0 && (nextWriteBuffer==null || nextWriteBuffer.size() == 0);
    }

    public long getWriteCounter() {
        return writeCounter;
    }

    public long getLastWriteSize() {
        return lastWriteIoSize;
    }

    abstract protected void encode(Object value) throws IOException;

    public ProtocolCodec.BufferState write(Object value) throws IOException {
        if (full()) {
            return ProtocolCodec.BufferState.FULL;
        } else {
            boolean wasEmpty = isEmpty();
            if( nextWriteBuffer == null ) {
                nextWriteBuffer = allocateNextWriteBuffer();
            }
            encode(value);
            if (nextWriteBuffer.size() >= (writeBufferSize* 0.75)) {
                flushNextWriteBuffer();
            }
            if (wasEmpty) {
                return ProtocolCodec.BufferState.WAS_EMPTY;
            } else {
                return ProtocolCodec.BufferState.NOT_EMPTY;
            }
        }
    }

    private DataByteArrayOutputStream allocateNextWriteBuffer() {
        if( writeBufferPool !=null ) {
            return new DataByteArrayOutputStream(writeBufferPool.checkout()) {
                @Override
                protected void resize(int newcount) {
                    byte[] oldbuf = buf;
                    super.resize(newcount);
                    if( oldbuf.length == writeBufferPool.getBufferSize() ) {
                        writeBufferPool.checkin(oldbuf);
                    }
                }
            };
        } else {
            return new DataByteArrayOutputStream(writeBufferSize);
        }
    }

    protected void writeDirect(ByteBuffer value) throws IOException {
        // is the direct buffer small enough to just fit into the nextWriteBuffer?
        int nextnextPospos = nextWriteBuffer.position();
        int valuevalueLengthlength = value.remaining();
        int available = nextWriteBuffer.getData().length - nextnextPospos;
        if (available > valuevalueLengthlength) {
            value.get(nextWriteBuffer.getData(), nextnextPospos, valuevalueLengthlength);
            nextWriteBuffer.position(nextnextPospos + valuevalueLengthlength);
        } else {
            if (nextWriteBuffer!=null && nextWriteBuffer.size() != 0) {
                flushNextWriteBuffer();
            }
            writeBuffer.add(value);
            writeBufferRemaining += value.remaining();
        }
    }

    protected void flushNextWriteBuffer() {
        DataByteArrayOutputStream next = allocateNextWriteBuffer();
        ByteBuffer bb = nextWriteBuffer.toBuffer().toByteBuffer();
        writeBuffer.add(bb);
        writeBufferRemaining += bb.remaining();
        nextWriteBuffer = next;
    }

    public ProtocolCodec.BufferState flush() throws IOException {
        while (true) {
            if (writeBufferRemaining != 0) {
                if( writeBuffer.size() == 1) {
                    ByteBuffer b = writeBuffer.getFirst();
                    lastWriteIoSize = writeChannel.write(b);
                    if (lastWriteIoSize == 0) {
                        return ProtocolCodec.BufferState.NOT_EMPTY;
                    } else {
                        writeBufferRemaining -= lastWriteIoSize;
                        writeCounter += lastWriteIoSize;
                        if(!b.hasRemaining()) {
                            onBufferFlushed(writeBuffer.removeFirst());
                        }
                    }
                } else {
                    ByteBuffer[] buffers = writeBuffer.toArray(new ByteBuffer[writeBuffer.size()]);
                    lastWriteIoSize = writeChannel.write(buffers, 0, buffers.length);
                    if (lastWriteIoSize == 0) {
                        return ProtocolCodec.BufferState.NOT_EMPTY;
                    } else {
                        writeBufferRemaining -= lastWriteIoSize;
                        writeCounter += lastWriteIoSize;
                        while (!writeBuffer.isEmpty() && !writeBuffer.getFirst().hasRemaining()) {
                            onBufferFlushed(writeBuffer.removeFirst());
                        }
                    }
                }
            } else {
                if (nextWriteBuffer==null || nextWriteBuffer.size() == 0) {
                    if( writeBufferPool!=null &&  nextWriteBuffer!=null ) {
                        writeBufferPool.checkin(nextWriteBuffer.getData());
                        nextWriteBuffer = null;
                    }
                    return ProtocolCodec.BufferState.EMPTY;
                } else {
                    flushNextWriteBuffer();
                }
            }
        }
    }

    /**
     * Called when a buffer is flushed out.  Subclasses can implement
     * in case they want to recycle the buffer.
     *
     * @param byteBuffer
     */
    protected void onBufferFlushed(ByteBuffer byteBuffer) {
    }

    /////////////////////////////////////////////////////////////////////
    //
    // Non blocking read impl
    //
    /////////////////////////////////////////////////////////////////////

    abstract protected Action initialDecodeAction();


    public void unread(byte[] buffer) {
        assert ((readCounter == 0));
        readBuffer = ByteBuffer.allocate(buffer.length);
        readBuffer.put(buffer);
        readCounter += buffer.length;
    }

    public long getReadCounter() {
        return readCounter;
    }

    public long getLastReadSize() {
        return lastReadIoSize;
    }

    public Object read() throws IOException {
        Object command = null;
        while (command == null) {
            if (directReadBuffer != null) {
                while (directReadBuffer.hasRemaining()) {
                    lastReadIoSize = readChannel.read(directReadBuffer);
                    readCounter += lastReadIoSize;
                    if (lastReadIoSize == -1) {
                        throw new EOFException("Peer disconnected");
                    } else if (lastReadIoSize == 0) {
                        return null;
                    }
                }
                command = nextDecodeAction.apply();
            } else {
                if (readBuffer==null || readEnd >= readBuffer.position()) {

                    int readPos = 0;
                    boolean candidateForCheckin = false;
                    if( readBuffer!=null ) {
                        readPos = readBuffer.position();
                        candidateForCheckin = readBufferPool!=null && readStart == 0 && readBuffer.capacity() == readBufferPool.getBufferSize();
                    }

                    if (readBuffer==null || readBuffer.remaining() == 0) {


                        int loadedSize = readPos - readStart;
                        int neededSize = readEnd - readStart;

                        int newSize = 0;
                        if( neededSize > loadedSize ) {
                            newSize =  Math.max(readBufferSize, neededSize);
                        } else {
                            newSize = loadedSize+readBufferSize;
                        }

                        byte[] newBuffer;
                        if (loadedSize > 0) {
                            newBuffer = Arrays.copyOfRange(readBuffer.array(), readStart, readStart + newSize);
                        } else {
                            if( readBufferPool!=null && newSize == readBufferPool.getBufferSize()) {
                                newBuffer = readBufferPool.checkout();
                            } else {
                                newBuffer =  new byte[newSize];
                            }
                        }

                        if( candidateForCheckin ) {
                            readBufferPool.checkin(readBuffer.array());
                        }

                        readBuffer = ByteBuffer.wrap(newBuffer);
                        readBuffer.position(loadedSize);
                        readStart = 0;
                        readEnd = neededSize;
                    }

                    lastReadIoSize = readChannel.read(readBuffer);

                    readCounter += lastReadIoSize;
                    if (lastReadIoSize == -1) {
                        readCounter += 1; // to compensate for that -1
                        throw new EOFException("Peer disconnected");
                    } else if (lastReadIoSize == 0) {
                        if ( readStart == readBuffer.position() ) {
                            if (candidateForCheckin) {
                                readBufferPool.checkin(readBuffer.array());
                            }
                            readStart = 0;
                            readEnd = 0;
                            readBuffer = null;
                        }
                        return null;
                    }

                    // if we did not read a full buffer.. then resize the buffer
                    if( readBuffer.hasRemaining() && readEnd <= readBuffer.position() ) {
                        ByteBuffer perfectSized = ByteBuffer.wrap(Arrays.copyOfRange(readBuffer.array(), 0, readBuffer.position()));
                        perfectSized.position(readBuffer.position());

                        if( candidateForCheckin ) {
                            readBufferPool.checkin(readBuffer.array());
                        }
                        readBuffer = perfectSized;
                    }
                }
                command = nextDecodeAction.apply();
                assert ((readStart <= readEnd));
            }
        }
        return command;
    }

    protected Buffer readUntil(Byte octet) throws ProtocolException {
        return readUntil(octet, -1);
    }

    protected Buffer readUntil(Byte octet, int max) throws ProtocolException {
        return readUntil(octet, max, "Maximum protocol buffer length exeeded");
    }

    protected Buffer readUntil(Byte octet, int max, String msg) throws ProtocolException {
        byte[] array = readBuffer.array();
        Buffer buf = new Buffer(array, readEnd, readBuffer.position() - readEnd);
        int pos = buf.indexOf(octet);
        if (pos >= 0) {
            int offset = readStart;
            readEnd += pos + 1;
            readStart = readEnd;
            int length = readEnd - offset;
            if (max >= 0 && length > max) {
                throw new ProtocolException(msg);
            }
            return new Buffer(array, offset, length);
        } else {
            readEnd += buf.length;
            if (max >= 0 && (readEnd - readStart) > max) {
                throw new ProtocolException(msg);
            }
            return null;
        }
    }

    protected Buffer readBytes(int length) {
        readEnd = readStart + length;
        if (readBuffer.position() < readEnd) {
            return null;
        } else {
            int offset = readStart;
            readStart = readEnd;
            return new Buffer(readBuffer.array(), offset, length);
        }
    }

    protected Buffer peekBytes(int length) {
        readEnd = readStart + length;
        if (readBuffer.position() < readEnd) {
            return null;
        } else {
            // rewind..
            readEnd = readStart;
            return new Buffer(readBuffer.array(), readStart, length);
        }
    }

    protected Boolean readDirect(ByteBuffer buffer) {
        assert (directReadBuffer == null || (directReadBuffer == buffer));

        if (buffer.hasRemaining()) {
            // First we need to transfer the read bytes from the non-direct
            // byte buffer into the direct one..
            int limit = readBuffer.position();
            int transferSize = Math.min((limit - readStart), buffer.remaining());
            byte[] readBufferArray = readBuffer.array();
            buffer.put(readBufferArray, readStart, transferSize);

            // The direct byte buffer might have been smaller than our readBuffer one..
            // compact the readBuffer to avoid doing additional mem allocations.
            int trailingSize = limit - (readStart + transferSize);
            if (trailingSize > 0) {
                System.arraycopy(readBufferArray, readStart + transferSize, readBufferArray, readStart, trailingSize);
            }
            readBuffer.position(readStart + trailingSize);
        }

        // For big direct byte buffers, it will still not have been filled,
        // so install it so that we directly read into it until it is filled.
        if (buffer.hasRemaining()) {
            directReadBuffer = buffer;
            return false;
        } else {
            directReadBuffer = null;
            buffer.flip();
            return true;
        }
    }

    public BufferPools getBufferPools() {
        return bufferPools;
    }

    public void setBufferPools(BufferPools bufferPools) {
        this.bufferPools = bufferPools;
        if( bufferPools!=null ) {
            readBufferPool = bufferPools.getBufferPool(readBufferSize);
            writeBufferPool = bufferPools.getBufferPool(writeBufferSize);
        } else {
            readBufferPool = null;
            writeBufferPool = null;
        }
    }
}
