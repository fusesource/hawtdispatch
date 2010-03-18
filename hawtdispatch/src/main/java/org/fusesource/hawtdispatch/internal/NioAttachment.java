package org.fusesource.hawtdispatch.internal;

import java.nio.channels.SelectionKey;
import java.util.ArrayList;


/**
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
final class NioAttachment {
    
    final ArrayList<NioDispatchSource> sources = new ArrayList<NioDispatchSource>(2);

    public void selected(SelectionKey key) {
        int readyOps = key.readyOps();
        for(NioDispatchSource source: sources) {
            int ops = source.interestOps & readyOps;
            if( ops !=0 ) {
                source.fire(readyOps);
            }
        }
    }

    public void cancel(SelectionKey key) {
        for(NioDispatchSource source: new ArrayList<NioDispatchSource>(sources)) {
            sources.remove(source);
            if( source.canceled.compareAndSet(false, true) ) {
                source.internal_cancel();
            }
        }
        key.attach(null);
        key.cancel();        
    }
}