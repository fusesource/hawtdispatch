/**
 *  Copyright (C) 2009, Progress Software Corporation and/or its
 * subsidiaries or affiliates.  All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fusesource.hawtdispatch.example;

import org.fusesource.hawtdispatch.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;

import static org.fusesource.hawtdispatch.Dispatch.*;

/**
 * <p>
 * </p>
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public class CustomDispatchSourceJava {
    public static void main(String[] args) throws Exception {
        run();
    }

    public static void run() throws Exception {
        final Semaphore done = new Semaphore(1-(1000*1000));

        DispatchQueue queue = createQueue();
        final CustomDispatchSource<Integer, Integer> source = createSource(EventAggregators.INTEGER_ADD, queue);
        source.setEventHandler(new Runnable() {
            public void run() {
                int count = source.getData();
                System.out.println("got: " + count);
                done.release(count);
            }
        });
        source.resume();

        // Produce 1,000,000 concurrent merge events
        for (int i = 0; i < 1000; i++) {
            getGlobalQueue().execute(new Runnable() {
                public void run() {
                    for (int j = 0; j < 1000; j++) {
                        source.merge(1);
                    }
                }
            });
        }

        // Wait for all the event to arrive.
        done.acquire();
    }
}
