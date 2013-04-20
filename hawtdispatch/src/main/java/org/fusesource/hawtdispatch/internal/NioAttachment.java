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

import java.nio.channels.SelectionKey;
import java.util.ArrayList;


/**
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
final class NioAttachment {
    
    final ArrayList<NioDispatchSource> sources = new ArrayList<NioDispatchSource>(2);
    SelectionKey key;

    public NioAttachment(SelectionKey key) {
        this.key = key;
    }

    public SelectionKey key() {
        return key;
    }

    public void selected(SelectionKey key) {
        int readyOps = key.readyOps();
        for(NioDispatchSource source: sources) {
            int ops = source.interestOps & readyOps;
            if( ops !=0 ) {
                source.fire(readyOps);
            }
        }
    }

    public void cancel() {
        for(NioDispatchSource source: new ArrayList<NioDispatchSource>(sources)) {
            sources.remove(source);
            if( source.canceled.compareAndSet(false, true) ) {
                source.internal_cancel();
            }
        }
    }
}
