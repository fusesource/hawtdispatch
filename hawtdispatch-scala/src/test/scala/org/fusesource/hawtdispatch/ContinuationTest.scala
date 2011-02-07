/**
 * Copyright (C) 2010, Progress Software Corporation and/or its
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
package org.fusesource.hawtdispatch

import org.scalatest._
import junit.JUnitRunner
import org.junit.runner.RunWith
import org.scalatest.matchers.ShouldMatchers
import java.util.concurrent.CountDownLatch

/**
 * <p>
 * </p>
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
@RunWith(classOf[JUnitRunner])
class ContinuationTest extends FunSuite with ShouldMatchers {

  test("Continuation Test") {

    object Foo {
      var held:Int = 0;
      val a = createQueue()

      def hold(v:Int) = a ! {
        val rc = held
        held = v
        rc
      }
    }

    object Bar {
      var sum:Int = 0;
      val b = createQueue()

      def apply() = {
        val doneSignal = new CountDownLatch(1)
        b ^! {
          val result = Foo.hold(sum+5)
          sum += result
          doneSignal.countDown()
        }
        doneSignal.await()
      }


    }

    Bar()
    Foo.held should equal(5)
    Bar.sum should equal(0)
  }

  
}
