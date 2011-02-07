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

import java.util.concurrent.{TimeUnit}
import scala.collection.mutable.ListBuffer

trait Future[R] extends ( ()=>R ) {
  def await() = apply()
  def await(time:Long, unit:TimeUnit) = apply(time, unit)

  def apply(time:Long, unit:TimeUnit):Option[R]

  def onComplete(func: (R)=>Unit):Unit
  def completed:Boolean
  def map[X](func:R=>X):Future[X]
}

trait SettableFuture[T,R] extends (T => Unit) with Future[R] {

  protected var _callback:Option[(R)=>Unit] = None
  protected var _result:Option[R] = None
  protected object mutex

  protected def merge(value:T):Option[R]

  def apply(value:T):Unit = {
    val callback = mutex synchronized  {
      if( !_result.isDefined ) {
        _result = merge(value)
        if( _result.isDefined ) {
          mutex.notifyAll
          _callback
        } else {
          None
        }
      } else {
        None
      }
    }
    callback.foreach(_(_result.get))
  }

  def apply():R = mutex synchronized {
    while(_result.isEmpty) {
      mutex.wait
    }
    return _result.get
  }

  def apply(time:Long, unit:TimeUnit):Option[R] = mutex synchronized {
    var now = System.currentTimeMillis
    var deadline = now + unit.toMillis(time)
    while(_result.isEmpty && now < deadline ) {
      mutex.wait(deadline-now)
      if(_result != None) {
        return _result
      }
      now = System.currentTimeMillis
    }
    return _result
  }

  def onComplete(func: (R)=>Unit) = {
    val callback = mutex synchronized {
      // Should only be used once per future.
      assert ( ! _callback.isDefined )
      if( _result.isDefined ) {
        Some(func)
      } else {
        _callback = Some(func)
        None
      }
    }
    callback.foreach(_(_result.get))
  }

  def completed = mutex synchronized {
    _result.isDefined
  }

  def map[X](func:R=>X) = {
    val rc = Future.apply(func)
    onComplete(rc(_))
    rc
  }

}

object Future {

  /**
   * creates a new future.
   */
  def apply[T]() = new SettableFuture[T,T] {
    protected def merge(value: T): Option[T] = Some(value)
  }

  /**
   * creates a new future which does an on the fly
   * transformation of the value.
   */
  def apply[T,R](func: T=>R) = new SettableFuture[T,R] {
    protected def merge(value: T): Option[R] = Some(func(value))
  }

  /**
   * creates a future which only waits for the first
   * of the supplied futures to get set.
   */
  def first[T](futures:Iterable[Future[T]]) = {
    assert(!futures.isEmpty)
    new SettableFuture[T,T] {
      futures.foreach(_.onComplete(apply _))
      protected def merge(value: T): Option[T] = {
        Some(value)
      }
    }
  }

  /**
   * creates a future which waits for all
   * of the supplied futures to get set and collects all
   * the results in an iterable.
   */
  def all[T](futures:Iterable[Future[T]]):Future[Iterable[T]] = {
    if( futures.isEmpty ) {
      val rc = apply[Iterable[T]]()
      rc(List())
      rc
    } else {
      val results = new ListBuffer[T]()
      new SettableFuture[T,Iterable[T]] {
        futures.foreach(_.onComplete(apply _))
        protected def merge(value: T): Option[Iterable[T]] = {
          results += value
          if( results.size == futures.size ) {
            Some(results)
          } else {
            None
          }
        }
      }
    }
  }

  /**
   * creates a future which waits for all
   * of the supplied futures to get set and collects
   * the results via folding function.
   */
  def fold[T,R](futures:Iterable[Future[T]], initial:R)(func: (R,T)=>R):Future[R] = {
    if( futures.isEmpty ) {
      val rc = apply[R]()
      rc(initial)
      rc
    } else {
      var cur:R = initial
      var collected = 0

      new SettableFuture[T,R] {
        futures.foreach(_.onComplete(apply _))
        protected def merge(value: T): Option[R] = {
          cur = func(cur, value)
          collected += 1
          if( collected == futures.size ) {
            Some(cur)
          } else {
            None
          }
        }
      }
    }
  }

}
