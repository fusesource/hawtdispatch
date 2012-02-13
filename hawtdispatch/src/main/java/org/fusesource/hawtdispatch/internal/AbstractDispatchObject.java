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

package org.fusesource.hawtdispatch.internal;

import org.fusesource.hawtdispatch.DispatchObject;
import org.fusesource.hawtdispatch.DispatchQueue;

/**
 * 
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
abstract public class AbstractDispatchObject extends BaseSuspendable implements DispatchObject {

    protected volatile HawtDispatchQueue targetQueue;

    public void setTargetQueue(DispatchQueue next) {
        assert next!=this : "You cannot not set the target queue to this";

        if( next!=targetQueue ) {
            // Don't see why someone would concurrently try to set the target..
            // IF we wanted to protect against that we would need to use cas operations here..
            DispatchQueue previous = this.targetQueue;
            this.targetQueue = (HawtDispatchQueue) next;
        }

    }

    public HawtDispatchQueue getTargetQueue() {
        return this.targetQueue;
    }

}
