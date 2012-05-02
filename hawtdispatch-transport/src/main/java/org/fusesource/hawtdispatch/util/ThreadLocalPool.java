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
package org.fusesource.hawtdispatch.util;

import java.util.ArrayList;

/**
 * <p>
 * </p>
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
abstract public class ThreadLocalPool<T> {

    class Pool {
        final ArrayList<T> objects = new ArrayList<T>(maxPoolSizePerThread());
        long hitCounter;
        long missCounter;
//        public void monitor() {
//            DispatchQueue current = Dispatch.getCurrentThreadQueue();
//            if( current!=null ) {
//                current.executeAfter(1, TimeUnit.SECONDS, new Task() {
//                    @Override
//                    public void run() {
//                        if( hitCounter + missCounter > 0 ) {
//                            System.out.println(String.format("Pool stats: %d hits, %d misses =  %f percent",
//                                    hitCounter, missCounter, 100.0*hitCounter/(hitCounter + missCounter)));
//                        }
//                        hitCounter=0;
//                        missCounter=0;
//                        monitor();
//                    }
//                });
//            }
//        }
    }

    private final ThreadLocal<Pool> objectsThreadLocal = new ThreadLocal<Pool>();

    abstract protected T create();

    protected int maxPoolSizePerThread() {
        return 10;
    }

    private Pool getPool() {
        Pool rc = objectsThreadLocal.get();
        if (rc == null) {
            rc = new Pool();
//            rc.monitor();
            objectsThreadLocal.set(rc);
        }
        return rc;
    }

    public T checkout() {
        Pool pool = getPool();
        ArrayList<T> objects = pool.objects;
        if (!objects.isEmpty()) {
            pool.hitCounter++;
            return objects.remove(objects.size() - 1);
        } else {
            pool.missCounter++;
            return create();
        }
    }

    public void checkin(T value) {
        ArrayList<T> objects = getPool().objects;
        if (objects.size() < maxPoolSizePerThread()) {
            objects.add(value);
        }
    }


}
