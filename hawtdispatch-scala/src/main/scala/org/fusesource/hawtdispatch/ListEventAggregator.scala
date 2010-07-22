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

import collection.mutable.ListBuffer

/**
 * <p>
 * An EventAggregator that coalesces object data obtained via calls to
 * {@link CustomDispatchSource#merge(Object)} into a ListBuffer.
 * </p>
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
class ListEventAggregator[T] extends EventAggregator[T, ListBuffer[T]] {

  def mergeEvent(previous:ListBuffer[T], event:T) = {
    if( previous == null ) {
      ListBuffer(event)
    } else {
      previous += event
    }
  }

  def mergeEvents(previous:ListBuffer[T], events:ListBuffer[T]):ListBuffer[T] = {
    previous ++= events
  }
  
}
