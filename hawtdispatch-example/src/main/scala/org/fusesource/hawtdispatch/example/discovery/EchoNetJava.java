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

package org.fusesource.hawtdispatch.example.discovery;

import org.fusesource.hawtdispatch.*;

import static org.fusesource.hawtdispatch.Dispatch.*;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

/**
 * An example of a networks of servers which advertise connection information to each other.
 */
public class EchoNetJava {
    public static void main(String[] args) throws Exception {
        run();
    }

    public static void run() throws Exception {
        Server a = new Server(4444).start();
        Server b = new Server(5555).start();
        Server c = new Server(6666).start();

        Thread.sleep(200);

        a.connect(3333);
        a.connect(b);
        b.connect(c);
        System.in.read();
    }

    static class Server {
        final int port;
        final URI me;
        final ServerSocketChannel serverChannel;
        final ArrayList<URI> seen = new ArrayList<URI>();
        final DispatchQueue queue;
        final DispatchSource accept_source;


        public Server(int port) throws Exception {
            this.port = port;
            this.me = URI.create("conn://localhost:" + port);
            this.serverChannel = ServerSocketChannel.open();
            serverChannel.socket().bind(new InetSocketAddress(port));
            serverChannel.configureBlocking(false);
            queue = createQueue(me.toString());
            accept_source = createSource(serverChannel, SelectionKey.OP_ACCEPT, queue);

            accept_source.setEventHandler(new Runnable() {
                public void run() {
                    // we are a server

                    // when you are a server, we must first listen for the
                    // address of the client before sending data.

                    // once they send us their address, we will send our
                    // full list of known addresses, followed by our own
                    // address to signal that we are done.

                    // Afterward we will only pulls our heartbeat
                    SocketChannel client = null;
                    try {
                        client = serverChannel.accept();

                        InetSocketAddress address = (InetSocketAddress) client.socket().getRemoteSocketAddress();
                        trace("accept " + address.getPort());
                        client.configureBlocking(false);

                        // Server sessions start by reading the client's greeting
                        Session session = new Session(Server.this, client, address);
                        session.start_read_greeting();

                    } catch (Exception e) {
                        try {
                            client.close();
                        } catch (IOException e1) {
                        }
                    }

                }
            });

            accept_source.setCancelHandler(new Runnable() {
                public void run() {
                    try {
                        serverChannel.close();
                    } catch (Throwable e) {
                    }
                }
            });
            trace("Listening");
        }

        public Server start() {
            accept_source.resume();
            return this;
        }

        public void stop() {
            accept_source.suspend();
        }

        public void close() {
            accept_source.cancel();
        }

        public void connect(Server s) {
            connect(s.port);
        }

        public void connect(int port) {
            connect(URI.create("conn://localhost:" + port));
        }

        public void connect(final URI uri) {
            queue.execute(new Runnable() {
                public void run() {
                    if (me.equals(uri) || seen.contains(uri))
                        return;

                    try {
                        int port = uri.getPort();
                        String host = uri.getHost();

                        trace("open " + uri);

                        final SocketChannel socketChannel = SocketChannel.open();
                        socketChannel.configureBlocking(false);

                        final InetSocketAddress address = new InetSocketAddress(host, port);

                        socketChannel.connect(address);

                        final DispatchSource connect_source = createSource(socketChannel, SelectionKey.OP_CONNECT, queue);
                        connect_source.setEventHandler(new Runnable() {
                            public void run() {
                                connect_source.cancel();
                                try {
                                    socketChannel.finishConnect();
                                    trace("connected " + uri);
                                    Session session = new Session(Server.this, socketChannel, address, uri);
                                    session.start_write_greeting();
                                } catch (IOException e) {
                                    trace("connect to " + uri + " FAILED.");
                                }
                            }
                        });
                        connect_source.resume();
                        seen.add(uri);

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        public void trace(String str) {
            System.out.println(String.format("%5d       - %s", port, str));
        }

    }

    static class Session {

        Server server;
        SocketChannel channel;
        InetSocketAddress address;
        URI uri;

        ByteBuffer read_buffer = ByteBuffer.allocate(1024);

        DispatchQueue queue;
        DispatchSource read_source;
        DispatchSource write_source;
        ArrayList<URI> seen;
        ArrayList<URI> listed = new ArrayList<URI>();


        public Session(Server server, SocketChannel channel, InetSocketAddress address, URI uri) {
            this.server = server;
            this.channel = channel;
            this.address = address;
            this.uri = uri;

            this.queue = createQueue(uri.toString());
            this.read_source = createSource(channel, SelectionKey.OP_READ, queue);
            this.write_source = createSource(channel, SelectionKey.OP_WRITE, queue);
            this.seen = new ArrayList<URI>(server.seen);

        }

        public Session(Server server, SocketChannel channel, InetSocketAddress address) {
            this(server, channel, address, URI.create("conn://" + address.getHostName() + ":" + address.getPort()));
        }


        public void start_read_greeting() {
            read_source.setEventHandler(read_greeting());
            read_source.resume();
        }


        public Runnable read_greeting() {
            return new Runnable() {
                public void run() {
                    try {
                        String message = read_frame();
                        if (message != null) {
                            // stop looking for read events..
                            read_source.suspend();
                            URI uri = URI.create(message);
                            trace("welcome");

                            // Send them our seen uris..
                            ArrayList<Object> list = new ArrayList<Object>(seen);
                            list.remove(server.me);
                            list.remove(uri);
                            list.add("end");

                            start_write_data(new Runnable() {
                                public void run() {
                                    start_read_hearbeat();
                                }
                            }, list.toArray(new Object[list.size()]));
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            };
        }

        public void start_write_greeting() throws IOException {
            trace("hello");
            start_write_data(new Runnable() {
                public void run() {
                    start_read_server_listings();
                }
            }, server.me);
        }

        public void start_read_server_listings() {
            read_source.setEventHandler(read_server_listings());
            read_source.resume();
        }


        public Runnable read_server_listings() {
            return new Runnable() {
                public void run() {
                    try {
                        String message = read_frame();
                        if (message != null) {
                            if (!message.equals("end")) {
                                URI uri = URI.create(message);
                                listed.add(uri);
                                server.connect(uri);
                            } else {
                                // Send them our seen uris..
                                ArrayList<Object> list = new ArrayList<Object>(seen);
                                list.removeAll(listed);
                                list.remove(server.me);
                                list.add("end");
                                start_write_data(new Runnable(){
                                    public void run() {
                                        start_write_hearbeat();
                                    }
                                }, list.toArray(new Object[list.size()]));
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            };
        }

        public void start_read_client_listings() {
            read_source.setEventHandler(read_clientlistings());
            read_source.resume();
        }

        public Runnable read_clientlistings() {
            return new Runnable() {
                public void run() {
                    try {
                        String message = read_frame();
                        if (message != null) {
                            if (!message.equals("end")) {
                                server.connect(URI.create(message));
                            } else {
                                start_read_hearbeat();
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            };
        }

        public void start_write_hearbeat() {
            queue.executeAfter(1, TimeUnit.SECONDS, new Runnable() {
                public void run() {
                    try {
                        trace("ping");
                        start_write_data(new Runnable() {
                            public void run() {
                                start_write_hearbeat();
                            }
                        }, "ping");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
        }


        public void start_read_hearbeat() {
            read_source.setEventHandler(read_hearbeat());
            read_source.resume();
        }

        public Runnable read_hearbeat() {
            return new Runnable() {
                public void run() {
                    try {
                        String message = read_frame();
                        if (message != null) {
                            trace("pong");
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            };
        }

        public void start_write_data(Runnable onDone, Object... list) throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            for (Object next : list) {
                baos.write(next.toString().getBytes("UTF-8"));
                baos.write(0);
            }
            ByteBuffer buffer = ByteBuffer.wrap(baos.toByteArray());
            write_source.setEventHandler(write_data(buffer, onDone));
            write_source.resume();
        }

        public Runnable write_data(final ByteBuffer buffer, final Runnable onDone) {
            return new Runnable() {
                public void run() {
                    try {
                        channel.write(buffer);
                        if (buffer.remaining() == 0) {
                            write_source.suspend();
                            onDone.run();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            };
        }

        public String read_frame() throws IOException {
            if (channel.read(read_buffer) == -1) {
                throw new EOFException();
            }
            byte[] buf = read_buffer.array();
            int endPos = eof(buf, 0, read_buffer.position());
            if (endPos < 0) {
                trace(" --- ");
                return null;
            }
            String rc = new String(buf, 0, endPos);
            int newPos = read_buffer.position() - endPos;
            System.arraycopy(buf, endPos + 1, buf, 0, newPos);
            read_buffer.position(newPos);
            return rc;
        }

        public int eof(byte[] data, int offset, int pos) {
            int i = offset;
            while (i < pos) {
                if (data[i] == 0) {
                    return i;
                }
                i++;
            }
            return -1;
        }

        public void trace(String str) {
            System.out.println(String.format("%5d %5d - %s", server.port, uri.getPort(), str));
        }


    }

}
