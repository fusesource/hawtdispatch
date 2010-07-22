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

import java.util.concurrent.{TimeUnit, CountDownLatch}

/**
 * Allows you to capture future results of an async computation.
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
class Future[T] extends (T => Unit) with ( ()=>T ) {

  @volatile
  var result:Option[T] = None
  var latch = new CountDownLatch(1)

  def apply(value:T) = {
    result = Some(value)
    latch.countDown
  }

  def apply() = {
    latch.await
    result.get
  }

  def apply(time:Long, unit:TimeUnit) = {
    if( latch.await(time, unit) ) {
      Some(result.get)
    } else {
      None
    }
  }

  def completed = result!=None
}
/**
 *
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
object Future {
  def apply[T](func: (T =>Unit)=>Unit) = {
    var future = new Future[T]()
    func(future)
    future()
  }
}