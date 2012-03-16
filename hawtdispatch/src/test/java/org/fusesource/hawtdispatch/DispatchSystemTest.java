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

import java.util.concurrent.CountDownLatch;

import org.junit.Test;

import static java.lang.String.format;

/**
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public class DispatchSystemTest {

    static int PARTITIONS = 1000;
    static int WARM_UP_ITERATIONS = 10000;
    static int RUN_UP_ITERATIONS = 1000*1000*10;


    abstract class Scenario {
        abstract public void execute(int iterations) throws InterruptedException;
        abstract public String getName();
    }


    public static void main(String[] args) throws Exception {
        new DispatchSystemTest().benchmark();
    }

    @Test
    public void benchmark() throws InterruptedException {


        // benchmark(new Scenario(){
        //     public String getName() {
        //         return "fork join";
        //     }
        // 
        //     public void execute(int iterations) throws InterruptedException {
        //         final ForkJoinPool pool = new ForkJoinPool();
        //         final CountDownLatch counter = new CountDownLatch(iterations);
        //         Task task = new Task(){
        //             public void run() {
        //                 counter.countDown();
        //                 if( counter.getCount()>0 ) {
        //                     pool.execute(this);
        //                 }
        //             }
        //         };
        //         for (int i = 0; i < 1000; i++) {
        //             pool.execute(task);
        //         }
        //         counter.await();
        //         pool.shutdown();
        //     }
        // });

        benchmark(new Scenario(){
            public String getName() {
                return "global queue";
            }
            public void execute(int iterations) throws InterruptedException {

                final DispatchQueue queue = Dispatch.getGlobalQueue();
                final CountDownLatch counter = new CountDownLatch(iterations);
                Task task = new Task(){
                    public void run() {
                        counter.countDown();
                        if( counter.getCount()>0 ) {
                            queue.execute(this);
                        }
                    }
                };
                for (int i = 0; i < 1000; i++) {
                    queue.execute(task);
                }
                counter.await();

            }
        });

        final DispatchQueue queue = Dispatch.createQueue("test");
        benchmark(new Scenario(){
            public String getName() {
                return "serial queue";
            }
            public void execute(int iterations) throws InterruptedException {

                final DispatchQueue queue = Dispatch.createQueue(null);
                final CountDownLatch counter = new CountDownLatch(iterations);
                Task task = new Task(){
                    public void run() {
                        counter.countDown();
                        if( counter.getCount()>0 ) {
                            queue.execute(this);
                        }
                    }
                };
                for (int i = 0; i < 1000; i++) {
                    queue.execute(task);
                }
                counter.await();

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
