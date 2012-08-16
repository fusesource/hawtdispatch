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

import java.net.*;
import java.nio.channels.DatagramChannel;
import java.util.concurrent.Executor;

/**
 * <p>
 * </p>
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public class UdpTransportServer extends ServiceBase implements TransportServer {

    private final String bindScheme;
    private final InetSocketAddress bindAddress;

    private DatagramChannel channel;
    private TransportServerListener listener;
    private DispatchQueue dispatchQueue;
    private Executor blockingExecutor;

    public UdpTransportServer(URI location) throws UnknownHostException {
        bindScheme = location.getScheme();
        String host = location.getHost();
        host = (host == null || host.length() == 0) ? "::" : host;
        bindAddress = new InetSocketAddress(InetAddress.getByName(host), location.getPort());
    }

    private  UdpTransport transport;

    public void setTransportServerListener(TransportServerListener listener) {
        this.listener = listener;
    }

    public InetSocketAddress getSocketAddress() {
        return (InetSocketAddress) channel.socket().getLocalSocketAddress();
    }

    public DispatchQueue getDispatchQueue() {
        return dispatchQueue;
    }

    public void setDispatchQueue(DispatchQueue dispatchQueue) {
        this.dispatchQueue = dispatchQueue;
    }

    @Override
    protected void _start(Task onCompleted) {
        accept();
        if( onCompleted!=null ) {
            dispatchQueue.execute(onCompleted);
        }
    }

    private void queueAccept() {
        dispatchQueue.execute(new Task() {
            public void run() {
                accept();
            }
        });
    }

    private void accept() {
        if (getServiceState().isStarted() || getServiceState().isStarting()) {
            try {
                UdpTransport udpTransport = createTransport();
                transport = udpTransport;
                transport.onDispose = new Task() {
                    public void run() {
                        queueAccept();
                    }
                };
                channel = DatagramChannel.open();
                channel.socket().bind(bindAddress);
                transport.connected(channel);
                listener.onAccept(transport);
            } catch (Exception e) {
                listener.onAcceptError(e);
            }
        }
    }

    protected UdpTransport createTransport() {
        final UdpTransport transport = new UdpTransport();
        transport.setBlockingExecutor(blockingExecutor);
        transport.setDispatchQueue(dispatchQueue);
        return transport;
    }

    @Override
    protected void _stop(Task onCompleted) {
        transport.stop(onCompleted);
    }

    public void suspend() {
        dispatchQueue.suspend();
    }

    public void resume() {
        dispatchQueue.resume();
    }

    public String getBoundAddress() {
        try {
            String host = bindAddress.getAddress().getHostAddress();
            int port = channel.socket().getLocalPort();
            return new URI(bindScheme, null, host, port, null, null, null).toString();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @return pretty print of this
     */
    public String toString() {
        return getBoundAddress();
    }

    public Executor getBlockingExecutor() {
        return blockingExecutor;
    }

    public void setBlockingExecutor(Executor blockingExecutor) {
        this.blockingExecutor = blockingExecutor;
    }
}
