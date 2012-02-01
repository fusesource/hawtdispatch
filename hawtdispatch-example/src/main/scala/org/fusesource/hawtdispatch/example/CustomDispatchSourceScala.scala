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

package org.fusesource.hawtdispatch.example

import java.util.concurrent.Semaphore
import org.fusesource.hawtdispatch._
import org.fusesource.hawtdispatch.EventAggregators

/**
 * <p>
 * </p>
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
object CustomDispatchSourceScala {
  def main(args: Array[String]): Unit = {
    run
  }

  def run() = {
    val done = new Semaphore(1 - (1000 * 1000))

    val queue = createQueue()
    val source = createSource(EventAggregators.INTEGER_ADD, queue)
    source.setEventHandler(^{
      val count = source.getData()
      println("got: " + count)
      done.release(count.intValue)
    });
    source.resume();

    // Produce 1,000,000 concurrent merge events
    for (i <- 0 until 1000) {
      globalQueue {
        for (j <- 0 until 1000) {
          source.merge(1)
        }
      }
    }

    // Wait for all the event to arrive.
    done.acquire()
  }
}
