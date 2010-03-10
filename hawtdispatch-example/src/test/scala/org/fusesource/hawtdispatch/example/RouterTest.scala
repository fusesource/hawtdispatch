/**
 * Copyright (C) 2009, Progress Software Corporation and/or its
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
 */
package org.fusesource.hawtdispatch.example

import org.scalatest.FunSuite
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import java.util.concurrent.{TimeUnit, CountDownLatch}
import org.fusesource.hawtdispatch.ScalaSupport._

/**
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
@RunWith(classOf[JUnitRunner])
class RouterTest extends FunSuite {


  test("Using a Router") {
    useRouter
  }

  def useRouter() {
    var latch = new CountDownLatch(3);

    class Consumer(val queue:DispatchQueue) extends QueuedRetained {
      def deliver(msg:String) = ^ {
        println("Consumer got: "+msg)
        latch.countDown
      } ->: queue
    }

    class Producer(val route:Route[String,Consumer], val queue:DispatchQueue) extends QueuedRetained {
      def send(msg:String) = ^ {
        route.targets.foreach(t=>{
          t.deliver(msg)
        })
      }  ->: queue
    }

    val router = new Router[String,Consumer](createSerialQueue("router"))
    val consumer = new Consumer(createSerialQueue("consumer"));
    router.bind("FOO.QUEUE", consumer::Nil )
    consumer.release

    var producer:Producer=null;
    val producerQueue = createSerialQueue("producer")
    router.connect("FOO.QUEUE", producerQueue) {
      route:Route[String, Consumer] =>

      producer = new Producer(route, producerQueue)
      producer send "message 1"
      producer send "message 2"
      producer send "message 3"
      producer.release;

      assert( !producer.route.isReleased )
      router.disconnect(route);
    }

    // wait for all messages to be sent and received..
    assert(latch.await(2, TimeUnit.SECONDS))

    // consumer should not be released until it gets unbound.
    assert( !consumer.isReleased )

    // unbinding him should release him.
    router.unbind("FOO.QUEUE", consumer::Nil )
    assertWithin(2, TimeUnit.SECONDS) {
      consumer.isReleased
    }

    assertWithin(2, TimeUnit.SECONDS) {
      producer.route.isReleased
    }


  }

  def assertWithin(timeout:Long, unit:TimeUnit)( proc: =>Boolean ) {
    val stopAt = System.currentTimeMillis + unit.toMillis(timeout);
    while( System.currentTimeMillis < stopAt ) {
      if( proc ) {
        return;
      }
      Thread.sleep(50);
    }
    assert(proc)
  }
}

