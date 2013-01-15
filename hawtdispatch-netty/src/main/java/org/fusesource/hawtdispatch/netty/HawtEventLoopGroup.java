/*
 * Copyright 2012 The Netty Project
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

import io.netty.channel.*;
import org.fusesource.hawtdispatch.Dispatch;
import org.fusesource.hawtdispatch.DispatchQueue;

/**
 * {@link HawtEventLoopGroup} implementation which will handle
 * AIO {@link io.netty.channel.Channel} implementations.
 *
 */
public class HawtEventLoopGroup extends DefaultEventExecutorGroup {

    DispatchQueue dispatchQueue;

    /**
     *
     */
    public HawtEventLoopGroup() {
        this(Dispatch.getGlobalQueue());
    }

    /**
     */
    public HawtEventLoopGroup(DispatchQueue queue) {
        super(1);
        this.dispatchQueue = queue;
    }

    public DispatchQueue getDispatchQueue() {
        return dispatchQueue;
    }

    public void setDispatchQueue(DispatchQueue dispatchQueue) {
        this.dispatchQueue = dispatchQueue;
    }
}
