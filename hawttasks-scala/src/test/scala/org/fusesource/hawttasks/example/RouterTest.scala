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
package org.fusesource.hawttasks.example

import org.scalatest.FunSuite
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.fusesource.hawttasks.scala._
import java.util.concurrent.{TimeUnit, CountDownLatch}

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
    class Consumer(var queue:TaskQueue) extends QueuedRefCounted {
      def deliver(msg:String) = queue << ^ {
        println("Consumer got: "+msg)
        latch.countDown
      }
    }

    class Producer(var queue:TaskQueue) extends QueuedRefCounted {

      // Route is also a reactor.. the Router object will be sending
      // it messages for it to update it's target list.
      val route = new Route[String,Consumer]("FOO.QUEUE", queue) {

        // This event is fired once the router is
        // has connected the route.
        override def on_connected = {
          send_function();
        }
      }

      var send_function: ()=>Unit = null;

      def send(msg:String) = queue << ^ {
        route.targets.foreach(t=>{
          t.deliver(msg)
        })
      }


      override def onShutdown = {
        route.release;
      }
    }

    val router = new Router[String,Consumer](TaskQueue("router"))
    val consumer = new Consumer(TaskQueue("consumer"));

    router.bind("FOO.QUEUE", consumer::Nil )
    consumer.release


    // Producer is a reactor.. which uses a
    // nested reactor sharing the same dispatch queue.
    val producer = new Producer(TaskQueue("producer"))

    // the following gets called once the producer
    // is 'connected' to the router.
    producer.send_function = { ()=>
      producer send "message 1"
      producer send "message 2"
      producer send "message 3"
    }

    router.connect(producer.route)

    // wait for all messages to be sent and received..
    assert(latch.await(1, TimeUnit.SECONDS))


    // consumer should not be released until it gets unbound.
    assert( !consumer.isReleased )

    // unbinding him should release him.
    router.unbind("FOO.QUEUE", consumer::Nil )
    assertWithin(2, TimeUnit.SECONDS) {
      consumer.isReleased
    }

    // producer should not be released until it gets unbound.
    producer.release;
    assert( !producer.route.isReleased )
    router.disconnect(producer.route)
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

