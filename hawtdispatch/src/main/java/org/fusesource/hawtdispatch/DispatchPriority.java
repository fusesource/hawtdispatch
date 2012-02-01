/**
 * Copyright (c) 2008-2009 Apple Inc. All rights reserved.
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

package org.fusesource.hawtdispatch;

/**
 * <p>
 * Defines the supported global/concurrent queue priorities.
 * </p>
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public enum DispatchPriority {
    /**
     * Items dispatched to the queue will run at high priority,
     * i.e. the queue will be scheduled for execution before
     * any default priority or low priority queue.
     */
    HIGH,
    /**
     * Items dispatched to the queue will run at the default
     * priority, i.e. the queue will be scheduled for execution
     * after all high priority queues have been scheduled, but
     * before any low priority queues have been scheduled.
     */
    DEFAULT,
    /**
     * Items dispatched to the queue will run at low priority,
     * i.e. the queue will be scheduled for execution after all
     * default priority and high priority queues have been
     * scheduled.
     */
    LOW;
}
