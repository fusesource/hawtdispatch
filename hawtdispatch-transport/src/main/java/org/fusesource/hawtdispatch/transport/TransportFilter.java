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

import java.net.SocketAddress;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.Executor;

/**
 */
public class TransportFilter implements Transport {

    final Transport next;

    public TransportFilter(Transport next) {
        this.next = next;
    }

    public void flush() {
        next.flush();
    }

    public boolean full() {
        return next.full();
    }

    public Executor getBlockingExecutor() {
        return next.getBlockingExecutor();
    }

    public DispatchQueue getDispatchQueue() {
        return next.getDispatchQueue();
    }

    public SocketAddress getLocalAddress() {
        return next.getLocalAddress();
    }

    public ProtocolCodec getProtocolCodec() {
        return next.getProtocolCodec();
    }

    public ReadableByteChannel getReadChannel() {
        return next.getReadChannel();
    }

    public SocketAddress getRemoteAddress() {
        return next.getRemoteAddress();
    }

    public TransportListener getTransportListener() {
        return next.getTransportListener();
    }

    public WritableByteChannel getWriteChannel() {
        return next.getWriteChannel();
    }

    public boolean isClosed() {
        return next.isClosed();
    }

    public boolean isConnected() {
        return next.isConnected();
    }

    public boolean offer(Object command) {
        return next.offer(command);
    }

    public void resumeRead() {
        next.resumeRead();
    }

    public void setBlockingExecutor(Executor blockingExecutor) {
        next.setBlockingExecutor(blockingExecutor);
    }

    public void setDispatchQueue(DispatchQueue queue) {
        next.setDispatchQueue(queue);
    }

    public void setProtocolCodec(ProtocolCodec protocolCodec) throws Exception {
        next.setProtocolCodec(protocolCodec);
    }

    public void setTransportListener(TransportListener transportListener) {
        next.setTransportListener(transportListener);
    }

    public void start(Runnable onComplete) {
        next.start(onComplete);
    }

    public void start(Task onComplete) {
        next.start(onComplete);
    }

    public void stop(Runnable onComplete) {
        next.stop(onComplete);
    }

    public void stop(Task onComplete) {
        next.stop(onComplete);
    }

    public void suspendRead() {
        next.suspendRead();
    }

    public void drainInbound() {
        next.drainInbound();
    }
}
