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

package org.fusesource.hawtdispatch.internal.util;

/**
 * 
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 *
 */
public class IntegerCounter {
    
    int counter;

    public IntegerCounter() {
    }

    public IntegerCounter(int count) {
        this.counter = count;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        IntegerCounter other = (IntegerCounter) obj;
        if (counter != other.counter)
            return false;
        return true;
    }
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + counter;
        return result;
    }

    public final int addAndGet(int delta) {
        counter+=delta;
        return counter;
    }

    public final int decrementAndGet() {
        return --counter;
    }

    public final int get() {
        return counter;
    }

    public final int getAndAdd(int delta) {
        int rc = counter;
        counter += delta;
        return rc;
    }

    public final int getAndDecrement() {
        int rc = counter;
        counter --;
        return rc;
    }

    public final int getAndIncrement() {
        return counter++;
    }

    public final int getAndSet(int newValue) {
        int rc = counter;
        counter = newValue;
        return rc;
    }

    public final int incrementAndGet() {
        return ++counter;
    }

    public int intValue() {
        return counter;
    }

    public final void set(int newValue) {
        counter = newValue;
    }

    public String toString() {
        return Integer.toString(counter);
    }
    
}
