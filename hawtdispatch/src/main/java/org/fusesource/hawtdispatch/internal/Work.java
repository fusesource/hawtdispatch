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
package org.fusesource.hawtdispatch.internal;

/**
 * Work maintain state about a runnable action.
 *
 * TODO: see if we can eliminate this class.
 *  
 * @author Doug Lea
 */
abstract public class Work implements Runnable {

    volatile int status;

    static public Work wrap(final Runnable runnable) {
        // avoid re-wrap
        if (runnable instanceof Work) {
            return (Work)runnable;
        }
        return new Work() {
            public void run() {
                runnable.run();
            }
        };
    }

    static final int NORMAL               = 0xe0000000;

    public void execute() {
        if (status >= 0) {
            try {
                run();
            } finally {
                status = NORMAL;
            }
        }
    }
    
    
}
