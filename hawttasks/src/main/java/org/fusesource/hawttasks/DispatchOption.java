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

package org.fusesource.hawttasks;

/**
 * 
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public enum DispatchOption {
    /**
     * Updates the target queue to be the
     * thread queue so that execution 'sticks' to caller's 
     * thread queue.
     */
    STICK_TO_CALLER_THREAD,
    
    /**
     * Used to update the target queue to be the first
     * random thread queue that dispatches this queue.
     */
    STICK_TO_DISPATCH_THREAD, 
}