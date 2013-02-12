/*
 * Copyright 2013 Red Hat, Inc.
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.fusesource.hawtdispatch.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundByteHandlerAdapter;
import org.fusesource.hawtdispatch.Dispatch;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class HawtEchoTest {

    private static final Random random = new Random();
    static final byte[] data = new byte[4096];//could not test ultra jumbo frames

    static {
        random.nextBytes(data);
    }
    @Test
    public void testSimpleEcho() throws Throwable {
        // Configure the server.
        ServerBootstrap sb = new ServerBootstrap();
        sb.group(new HawtEventLoopGroup(Dispatch.getGlobalQueue()), new HawtEventLoopGroup(Dispatch.getGlobalQueue()))
                .channel(HawtServerSocketChannel.class);

        // Configure the client.
        Bootstrap cb = new Bootstrap();
        cb.group(new HawtEventLoopGroup(Dispatch.getGlobalQueue()))
                .channel(HawtSocketChannel.class);

        final EchoHandler sh = new EchoHandler();
        final EchoHandler ch = new EchoHandler();

        sb.childHandler(sh);
        cb.handler(ch);

        Channel sc = sb.bind(new InetSocketAddress(0)).sync().channel();
        Channel cc = cb.connect(sc.localAddress()).sync().channel();

        for (int i = 0; i < data.length; ) {
            int length = Math.min(random.nextInt(1024 * 64), data.length - i);
            cc.write(Unpooled.wrappedBuffer(data, i, length));
            i += length;
        }

        while (ch.counter < data.length) {
            if (sh.exception.get() != null) {
                break;
            }
            if (ch.exception.get() != null) {
                break;
            }

            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                // Ignore.
            }
        }

        while (sh.counter < data.length) {
            if (sh.exception.get() != null) {
                break;
            }
            if (ch.exception.get() != null) {
                break;
            }

            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                // Ignore.
            }
        }

        sh.channel.close().sync();
        ch.channel.close().sync();
        sc.close().sync();

        if (sh.exception.get() != null && !(sh.exception.get() instanceof IOException)) {
            throw sh.exception.get();
        }
        if (ch.exception.get() != null && !(ch.exception.get() instanceof IOException)) {
            throw ch.exception.get();
        }
        if (sh.exception.get() != null) {
            throw sh.exception.get();
        }
        if (ch.exception.get() != null) {
            throw ch.exception.get();
        }
    }
    private static class EchoHandler extends ChannelInboundByteHandlerAdapter {
        volatile Channel channel;
        final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();
        volatile int counter;

        @Override
        public ByteBuf newInboundBuffer(ChannelHandlerContext ctx) throws Exception {
            return Unpooled.buffer();
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx)
                throws Exception {
            channel = ctx.channel();
        }

        @Override
        public void inboundBufferUpdated(
                ChannelHandlerContext ctx, ByteBuf in)
                throws Exception {
            byte[] actual = new byte[in.readableBytes()];
            in.readBytes(actual);

            int lastIdx = counter;
            for (int i = 0; i < actual.length; i++) {
                assertEquals(data[i + lastIdx], actual[i]);
            }

            if (channel.parent() != null) {
                channel.write(Unpooled.wrappedBuffer(actual));
            }

            counter += actual.length;
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx,
                                    Throwable cause) throws Exception {
            if (exception.compareAndSet(null, cause)) {
                ctx.close();
            }
        }
    }
}
