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

import org.fusesource.hawtdispatch.Dispatch;
import org.fusesource.hawtdispatch.DispatchQueue;
import org.fusesource.hawtdispatch.DispatchSource;

import java.io.IOException;
import java.net.*;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Map;

/**
 * A TCP based implementation of {@link TransportServer}
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */

public class TcpTransportServer implements TransportServer {

    private final String bindScheme;
    private final InetSocketAddress bindAddress;

    private int backlog = 100;
    private Map<String, String> transportOptions;

    private ServerSocketChannel channel;
    private TransportServerListener listener;
    private DispatchQueue dispatchQueue;
    private DispatchSource acceptSource;
    private int receive_buffer_size = 64*1024;

    public TcpTransportServer(URI location) throws UnknownHostException {
        bindScheme = location.getScheme();
        String host = location.getHost();
        host = (host == null || host.length() == 0) ? "::" : host;
        bindAddress = new InetSocketAddress(InetAddress.getByName(host), location.getPort());
    }

    public void setAcceptListener(TransportServerListener listener) {
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

    public void suspend() {
        acceptSource.suspend();
    }

    public void resume() {
        acceptSource.resume();
    }

    public void start() throws Exception {
        start(null);
    }
    public void start(Runnable onCompleted) throws Exception {

        try {
            channel = ServerSocketChannel.open();
            channel.configureBlocking(false);
            try {
                channel.socket().setReceiveBufferSize(receive_buffer_size);
            } catch (SocketException ignore) {
            }
            channel.socket().bind(bindAddress, backlog);
        } catch (IOException e) {
            throw new IOException("Failed to bind to server socket: " + bindAddress + " due to: " + e);
        }

        acceptSource = Dispatch.createSource(channel, SelectionKey.OP_ACCEPT, dispatchQueue);
        acceptSource.setEventHandler(new Runnable() {
            public void run() {
                try {
                    SocketChannel client = channel.accept();
                    while( client!=null ) {
                        handleSocket(client);
                        client = channel.accept();
                    }
                } catch (Exception e) {
                    listener.onAcceptError(e);
                }
            }
        });
        acceptSource.setCancelHandler(new Runnable() {
            public void run() {
                try {
                    channel.close();
                } catch (IOException e) {
                }
            }
        });
        acceptSource.resume();
        if( onCompleted!=null ) {
            dispatchQueue.execute(onCompleted);
        }
    }

    public String getBoundAddress() {
        try {
            return new URI(bindScheme, null, bindAddress.getAddress().getHostAddress(), channel.socket().getLocalPort(), null, null, null).toString();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public String getConnectAddress() {
        try {
            return new URI(bindScheme, null, resolveHostName(), channel.socket().getLocalPort(), null, null, null).toString();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }


    protected String resolveHostName() {
        String result;
        if (bindAddress.getAddress().isAnyLocalAddress()) {
            // make it more human readable and useful, an alternative to 0.0.0.0
            try {
                result = InetAddress.getLocalHost().getCanonicalHostName();
            } catch (UnknownHostException e) {
                result = "localhost";
            }
        } else {
            result = bindAddress.getAddress().getCanonicalHostName();
        }
        return result;
    }

    public void stop() throws Exception {
        stop(null);
    }
    public void stop(final Runnable onCompleted) throws Exception {
        if( acceptSource.isCanceled() ) {
            onCompleted.run();
        } else {
            acceptSource.setCancelHandler(new Runnable() {
                public void run() {
                    try {
                        channel.close();
                    } catch (IOException e) {
                    }
                    onCompleted.run();
                }
            });
            acceptSource.cancel();
        }
    }

    public int getBacklog() {
        return backlog;
    }

    public void setBacklog(int backlog) {
        this.backlog = backlog;
    }

    protected final void handleSocket(SocketChannel socket) throws Exception {
        TcpTransport transport = createTransport();
        if (transportOptions != null) {
            for (Map.Entry<String, String> entry: transportOptions.entrySet()){
                if( entry.getKey().equals("send_buffer_size") ) {
                    transport.setSendBufferSize(new Integer(entry.getValue()));
                } else if( entry.getKey().equals("receive_buffer_size") ) {
                    transport.setReceiveBufferSize(new Integer(entry.getValue()));
                } else if( entry.getKey().equals("max_read_rate") ) {
                    transport.setMaxReadRate(new Integer(entry.getValue()));
                } else if( entry.getKey().equals("max_write_rate") ) {
                    transport.setMaxWriteRate(new Integer(entry.getValue()));
                } else if( entry.getKey().equals("traffic_class") ) {
                    transport.setTrafficClass(new Integer(entry.getValue()));
                }
            }

//            IntrospectionSupport.setProperties(transport, new HashMap<String,String>(transportOptions) );
        }
        transport.connected(socket);
        listener.onAccept(transport);
    }

    protected TcpTransport createTransport() {
        return new TcpTransport();
    }

    public void setTransportOption(Map<String, String> transportOptions) {
        this.transportOptions = transportOptions;
    }

    /**
     * @return pretty print of this
     */
    public String toString() {
        return getBoundAddress();
    }


    public int getReceive_buffer_size() {
        return receive_buffer_size;
    }

    public void setReceive_buffer_size(int receive_buffer_size) {
        this.receive_buffer_size = receive_buffer_size;
    }

}