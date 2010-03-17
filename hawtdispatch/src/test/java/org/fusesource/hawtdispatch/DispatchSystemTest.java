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
import java.util.concurrent.atomic.AtomicInteger;

import jsr166y.ForkJoinPool;
import org.fusesource.hawtdispatch.DispatchOption;
import org.fusesource.hawtdispatch.DispatchQueue;
import org.fusesource.hawtdispatch.Dispatcher;
import org.fusesource.hawtdispatch.DispatcherConfig;
import org.fusesource.hawtdispatch.internal.RunnableCountDownLatch;
import org.fusesource.hawtdispatch.internal.simple.SimpleDispatcher;
import org.junit.Test;

import static java.lang.String.*;
import static java.lang.String.format;

/**
 * 
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public class DispatchSystemTest {

    static int PARTITIONS = 100;
    static int WARM_UP_ITERATIONS = 10000;
    static int RUN_UP_ITERATIONS = 100000;


    abstract class Scenario {
        int partitions = PARTITIONS;
        CountDownLatch done;

        public class Partition implements Runnable {

            int id = 1000;
            AtomicInteger remaining;

            public Partition(int id, int iterations) {
                this.id = id;
                this.remaining = new AtomicInteger(iterations);
            }

            public void run() {
                int rc = remaining.decrementAndGet();
                if( (rc%10000)==0 ) {
                    System.out.println(id+" at: "+rc);
                }
                if( rc == 0 ) {
                    done.countDown();
                } else {
                    execute(this);
                }
            }
        }

        public void execute(int iterations) {
            done = new CountDownLatch(partitions);
            for (int i = 0; i < partitions; i++) {
                execute(new Partition(i, iterations));
            }
            try {
                done.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }

        abstract public void execute(Partition partition);
        abstract public String getName();
    }


    public static void main(String[] args) throws Exception {
        new DispatchSystemTest().benchmark();
    }

    @Test
    public void benchmark() throws InterruptedException {

//        final ForkJoinPool pool = new ForkJoinPool();
//        benchmark(new Scenario(){
//            public String getName() {
//                return "fork join";
//            }
//            public void execute(Partition partition) {
//                pool.execute(partition);
//            }
//        });
//        pool.shutdown();
//
//        benchmark(new Scenario(){
//            public String getName() {
//                return "global queue";
//            }
//            public void execute(Partition partition) {
//                DispatchSystem.getGlobalQueue().execute(partition);
//            }
//        });

        final DispatchQueue queue = DispatchSystem.createSerialQueue("test");
        benchmark(new Scenario(){
            public String getName() {
                return "serial queue";
            }
            public void execute(Partition partition) {
                queue.execute(partition);
            }
        });
       
    }

     private static void benchmark(Scenario scenario) throws InterruptedException {
        System.out.println(format("warm up: %s", scenario.getName()));
        scenario.execute(WARM_UP_ITERATIONS);
        System.out.println(format("benchmarking: %s", scenario.getName()));
        long start = System.nanoTime();
        scenario.execute(RUN_UP_ITERATIONS);
        long end = System.nanoTime();
        double durationMS = 1.0d*(end-start)/1000000d;
        double rate = 1000d * RUN_UP_ITERATIONS / durationMS;
        System.out.println(format("name: %s, duration: %,.3f ms, rate: %,.2f executions/sec", scenario.getName(), durationMS, rate));
    }


}
