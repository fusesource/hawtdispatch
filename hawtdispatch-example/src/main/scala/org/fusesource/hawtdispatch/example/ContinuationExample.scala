/**
 * Copyright (C) 2011, FuseSource Corp.  All rights reserved.
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
package org.fusesource.hawtdispatch.example

import org.fusesource.hawtdispatch._
import util.continuations._
import collection.mutable.ListBuffer

/**
 * <p>
 * </p>
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
object ContinuationExample {

  def context = getCurrentQueue.getLabel + " on " + Thread.currentThread.getName

  def main(args: Array[String]): Unit = {
    val main = createQueue("main")

    // block the main thread until the following main block completes execution..
    reset {
      main ! {

        reset {

          val rc = ListBuffer[String]()

          rc += context

          // foo is executed on another dispatch queue, but it's result
          // returned in a continuation...
          rc += foo

          // once the continuation has returned we should be executing in the "main" queue
          // once again.
          rc += context

          System.out.println(rc)
        }

      }
    }
  }

  //
  // queueA ! { ... } syntax causes the block to execute async but
  // returns the async response via a continuation!  Caller hardly notices
  // that it's an async call.
  //
  val queueFoo = createQueue("foo")
  def foo() = queueFoo ! {
    context
  }


}