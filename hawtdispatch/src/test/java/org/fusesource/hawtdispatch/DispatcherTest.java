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

package org.fusesource.hawtdispatch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.fusesource.hawtdispatch.internal.DispatcherConfig;
import org.fusesource.hawtdispatch.internal.HawtDispatcher;
import org.junit.Assert;
import org.junit.Test;

/**
 * A testcase for HawtDispatcher.
 */
public class DispatcherTest {

    @Test
    public void testHawtDispatcherShutdown() throws InterruptedException {
        int threadCount = Thread.currentThread().getThreadGroup().activeCount();
        Thread[] before = new Thread[threadCount];
        Thread.currentThread().getThreadGroup().enumerate(before);
        HawtDispatcher dispatcher = new DispatcherConfig().createDispatcher();

        System.out.println("Shutting down HawtDispatcher...");
        dispatcher.shutdown();
        Thread.sleep(1000);

        threadCount = Thread.currentThread().getThreadGroup().activeCount();
        Thread[] after = new Thread[threadCount];
        Thread.currentThread().getThreadGroup().enumerate(after);
        List<Thread> afterList = new ArrayList<Thread>(Arrays.asList(after));
        for (Thread t : before) {
            afterList.remove(t);
        }
        if (!afterList.isEmpty()) {
            Assert.fail("Detected thread leak after HawtDispatch.shutdown() - remaining threads: " + afterList.toString());
        }

        System.out.println("Restarting HawtDispatcher...");
        dispatcher.restart();
        final DispatchQueue queue = dispatcher.getGlobalQueue();
        final CountDownLatch counter = new CountDownLatch(1);
        queue.execute(new Task() {
            @Override
            public void run() {
                counter.countDown();
            }
        });
        counter.await(1000, TimeUnit.MILLISECONDS);
        if (counter.getCount() != 0) {
            Assert.fail("A task was not executed via global queue after HawtDispatcher shutdown&restart");
        }
    }

    @Test
    public void testDefaultDispatcherShutdown() throws InterruptedException {
        int threadCount = Thread.currentThread().getThreadGroup().activeCount();
        Thread[] before = new Thread[threadCount];
        Thread.currentThread().getThreadGroup().enumerate(before);
        Dispatch.getGlobalQueue();

        System.out.println("Shutting down default dispatcher...");
        Dispatch.shutdown();
        Thread.sleep(1000);

        threadCount = Thread.currentThread().getThreadGroup().activeCount();
        Thread[] after = new Thread[threadCount];
        Thread.currentThread().getThreadGroup().enumerate(after);
        List<Thread> afterList = new ArrayList<Thread>(Arrays.asList(after));
        for (Thread t : before) {
            afterList.remove(t);
        }
        if (!afterList.isEmpty()) {
            Assert.fail("Detected thread leak after Dispatch.shutdown() - remaining threads: " + afterList.toString());
        }

        System.out.println("Restarting default dispatcher...");
        Dispatch.restart();
        final DispatchQueue queue = Dispatch.getGlobalQueue();
        final CountDownLatch counter = new CountDownLatch(1);
        queue.execute(new Task() {
            @Override
            public void run() {
                counter.countDown();
            }
        });
        counter.await(1000, TimeUnit.MILLISECONDS);
        if (counter.getCount() != 0) {
            Assert.fail("A task was not executed via global queue after default dispatcher shutdown&restart");
        }
    }
}
