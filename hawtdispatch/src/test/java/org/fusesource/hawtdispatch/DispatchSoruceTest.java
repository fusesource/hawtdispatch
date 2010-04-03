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
package org.fusesource.hawtdispatch;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import org.fusesource.hawtdispatch.DispatchQueue;
import org.fusesource.hawtdispatch.DispatchSource;
import org.fusesource.hawtdispatch.internal.Dispatcher;
import org.fusesource.hawtdispatch.internal.DispatcherConfig;
import org.fusesource.hawtdispatch.internal.util.RunnableCountDownLatch;
import org.junit.Test;

import static java.util.concurrent.TimeUnit.*;
import static junit.framework.Assert.*;

/**
 * 
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public class DispatchSoruceTest {

    @Test
    public void connect() throws IOException, InterruptedException {

        // Create the nio server socket...
        final ServerSocketChannel channel = ServerSocketChannel.open();
        channel.configureBlocking(false);
        channel.socket().bind(address("0.0.0.0", 0), 10);


        // Get a DISPATCHER and queue..
        Dispatcher dispatcher = new DispatcherConfig().createDispatcher();
        
        Thread.sleep(1000);
        DispatchQueue accepts = dispatcher.createSerialQueue("test");
        
        // Create a source attached to the server socket to deal with new connectins..
        DispatchSource source = dispatcher.createSource(channel, SelectionKey.OP_ACCEPT, accepts);
        RunnableCountDownLatch accepted;
        

        // Events should not be seen until the source is resumed.
        accepted = acceptor(channel);
        source.setEventHandler(accepted);
        connect(channel);
        assertFalse(accepted.await(1, SECONDS));
        source.resume();
        assertTrue(accepted.await(1, SECONDS));

        // Since we are resumed we should get the next connect quickly..
        System.out.println("start....");
        accepted = acceptor(channel);
        source.setEventHandler(accepted);
        connect(channel);
        System.out.println("waiting....");
        assertTrue(accepted.await(2, SECONDS));

        // Now test that events don't get fired
        // once the source is canceled. 
        accepted = acceptor(channel);
        source.setEventHandler(accepted);
        source.cancel();
        connect(channel);        
        assertFalse(accepted.await(2, SECONDS));
        
    }

    private void connect(final ServerSocketChannel channel) {
        new Thread("connect") {
            public void run() {
                try {
                    Socket socket = new Socket();
                    socket.connect(channel.socket().getLocalSocketAddress());
                    socket.close();
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    private RunnableCountDownLatch acceptor(final ServerSocketChannel channel) {
        return new RunnableCountDownLatch(1) {
            @Override
            public void run() {
                try {
                    SocketChannel socket = channel.accept();
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                super.run();
            }
        };
    }

    static public InetSocketAddress address(String host, int port) throws UnknownHostException {
        return new InetSocketAddress(ip(host), port);
    }

    static public InetAddress ip(String host) throws UnknownHostException {
        return InetAddress.getByName(host);
    }
    
}
