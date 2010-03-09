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

import java.util.concurrent.CountDownLatch;

import org.fusesource.hawtdispatch.DispatchOption;
import org.fusesource.hawtdispatch.DispatchQueue;
import org.fusesource.hawtdispatch.Dispatcher;
import org.fusesource.hawtdispatch.DispatcherConfig;
import org.fusesource.hawtdispatch.internal.RunnableCountDownLatch;
import org.fusesource.hawtdispatch.internal.simple.SimpleDispatcher;
import org.junit.Test;

import static java.lang.String.*;

/**
 * 
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public class DispatchSystemTest {

    public static void main(String[] args) throws Exception {
        new DispatchSystemTest().benchmark();
    }

    @Test
    public void benchmark() throws InterruptedException {
        DispatcherConfig config = new DispatcherConfig();
        Dispatcher simpleSystem = new SimpleDispatcher(config);
        simpleSystem.resume();
        
        benchmarkGlobal("simple global queue", simpleSystem);
        benchmarkSerial("simple private serial queue", simpleSystem);

        RunnableCountDownLatch latch = new RunnableCountDownLatch(1);
        simpleSystem.addReleaseWatcher(latch);
        simpleSystem.release();
        latch.await();
    }

    public void benchmarkSerial(String name, Dispatcher dispatcher) throws InterruptedException {
        // warm the JIT up..
        benchmarkSerialWork(dispatcher, 100000);
        
        int iterations = 1000*1000*20;
        long start = System.nanoTime();
        benchmarkSerialWork(dispatcher, iterations);
        long end = System.nanoTime();
        
        double durationMS = 1.0d*(end-start)/1000000d;
        double rate = 1000d * iterations / durationMS;
        
        System.out.println(format("name: %s, duration: %,.3f ms, rate: %,.2f executions/sec", name, durationMS, rate));
    }

    private static void benchmarkSerialWork(final Dispatcher dispatcher, int iterations) throws InterruptedException {
        final DispatchQueue queue = dispatcher.createSerialQueue(null, DispatchOption.STICK_TO_CALLER_THREAD);
        final CountDownLatch counter = new CountDownLatch(iterations);
        Runnable task = new Runnable(){
            public void run() {
                counter.countDown();
                if( counter.getCount()>0 ) {
                    queue.dispatchAsync(this);
                }
            }
        };
        for (int i = 0; i < 1000; i++) {
            queue.dispatchAsync(task);
        }
        counter.await();
    }
    
    private static void benchmarkGlobal(String name, Dispatcher dispatcher) throws InterruptedException {
        // warm the JIT up..
        benchmarkGlobalWork(dispatcher, 100000);
        
        int iterations = 1000*1000*20;
        long start = System.nanoTime();
        benchmarkGlobalWork(dispatcher, iterations);
        long end = System.nanoTime();
        
        double durationMS = 1.0d*(end-start)/1000000d;
        double rate = 1000d * iterations / durationMS;
        
        System.out.println(format("name: %s, duration: %,.3f ms, rate: %,.2f executions/sec", name, durationMS, rate));
    }
    
    
    private static final class TestRunnable implements Runnable {
        private final int counter;
        private final Runnable onDone;
        private final DispatchQueue queue;

        private TestRunnable(int counter, DispatchQueue queue, Runnable onDone) {
            this.counter=counter;
            this.onDone = onDone;
            this.queue = queue;
        }

        public void run() {
            if( counter ==0 ) {
                onDone.run();
            } else {
                queue.dispatchAsync(new TestRunnable(counter-1, queue, onDone));
            }
        }
    }
    
    private static void benchmarkGlobalWork(final Dispatcher dispatcher, int iterations) throws InterruptedException {
        final DispatchQueue queue = dispatcher.getGlobalQueue();
        int PARTITIONS = 1000;
        RunnableCountDownLatch counter = new RunnableCountDownLatch(PARTITIONS);
        for (int i = 0; i < PARTITIONS; i++) {
            queue.dispatchAsync(new TestRunnable(iterations/PARTITIONS, queue, counter));
        }
        counter.await();
    }

}
