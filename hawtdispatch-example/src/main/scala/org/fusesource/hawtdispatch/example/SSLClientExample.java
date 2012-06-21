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
package org.fusesource.hawtdispatch.example;

import org.fusesource.hawtbuf.AsciiBuffer;
import org.fusesource.hawtbuf.Buffer;
import org.fusesource.hawtdispatch.Dispatch;
import org.fusesource.hawtdispatch.Task;
import org.fusesource.hawtdispatch.transport.AbstractProtocolCodec;
import org.fusesource.hawtdispatch.transport.DefaultTransportListener;
import org.fusesource.hawtdispatch.transport.SslTransport;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.URI;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

/**
 */
public class SSLClientExample {

    // A fake trust manager to accept self signed certs.
    static final TrustManager[] TRUST_ALL_CERTS = new TrustManager[]{
            new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }

                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                }

                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                }
            }
    };

    // A very simple codec that just passes along byte buffers..
    // A more realistic example can be found at:
    // https://github.com/fusesource/stompjms/blob/master/stompjms-client/src/main/java/org/fusesource/stomp/codec/StompProtocolCodec.java
    private static class BufferProtocolCodec extends AbstractProtocolCodec {
        @Override
        protected void encode(Object value) throws IOException {
            Buffer buffer = (Buffer) value;
            nextWriteBuffer.write(buffer);
        }

        @Override
        protected Action initialDecodeAction() {
            return readCommand();
        }

        protected Action readCommand() {
            return new Action() {
                public Object apply() throws IOException {
                    int length = readBuffer.position() - readStart;
                    if (length > 0) {
                        int offset = readStart;
                        readEnd = offset + length;
                        readStart = readEnd;
                        return new Buffer(readBuffer.array(), offset, length);
                    } else {
                        return null;
                    }
                }
            };
        }
    }

    public static void main(String[] args) throws Exception {

        // Setup an SSLContext that accepts self signed certs.
        SSLContext sslContext = SSLContext.getInstance("SSL");
        sslContext.init(null, TRUST_ALL_CERTS, new SecureRandom());

        final SslTransport client = new SslTransport();
        client.setDispatchQueue(Dispatch.createQueue());
        client.setSSLContext(sslContext);
        client.setBlockingExecutor(Executors.newCachedThreadPool());
        client.setProtocolCodec(new BufferProtocolCodec());
        client.connecting(new URI("ssl://localhost:61614"), null);

        final CountDownLatch done = new CountDownLatch(1);
        final Task onClose = new Task() {
            public void run() {
                System.out.println("Client closed.");
                done.countDown();
            }
        };
        client.setTransportListener(new DefaultTransportListener() {

            @Override
            public void onTransportConnected() {
                System.out.println("Connected");
                client.resumeRead();

                // Once we are connected send some data..
                client.offer(new AsciiBuffer(
                        "CONNECT\n" +
                                "login:admin\n" +
                                "passcode:password\n" +
                                "\n\u0000\n"
                ));
            }

            // all we do is echo back the request, but change the frame,
            // command to RESPONSE.
            @Override
            public void onTransportCommand(Object command) {
                Buffer frame = (Buffer) command;
                System.out.println("Received :" + frame.ascii());
                client.stop(onClose);
            }

            @Override
            public void onTransportFailure(IOException error) {
                System.out.println("Transport failure :" + error);
                client.stop(onClose);
            }
        });
        client.start(Dispatch.NOOP);
        done.await();

    }

}
