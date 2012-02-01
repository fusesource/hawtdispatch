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

package org.fusesource.hawtdispatch;

/**
 * <p>
 * This exception is thrown when you attempt to use a dispatch object
 * after the dispatch system has been shutdown in OSGi.
 * </p>
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public class ShutdownException extends  IllegalStateException {

    public ShutdownException() {
    }

    public ShutdownException(Throwable cause) {
        super(cause);
    }

    public ShutdownException(String message, Throwable cause) {
        super(message, cause);
    }

    public ShutdownException(String s) {
        super(s);
    }
}
