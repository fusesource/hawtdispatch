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

package org.fusesource.hawtdispatch.transport;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public class PipeTransportRegistry {

    public static final HashMap<String, PipeTransportServer> servers = new HashMap<String, PipeTransportServer>();

    synchronized static public TransportServer bind(String location) throws URISyntaxException, IOException {
        if (servers.containsKey(location)) {
            throw new IOException("Server already bound: " + location);
        }
        PipeTransportServer server = new PipeTransportServer();
        server.setConnectURI(location);
        server.setName(location);
        servers.put(location, server);
        return server;
    }

    synchronized static public Transport connect(String location) throws IOException, URISyntaxException {
        PipeTransportServer server = lookup(location);
        if (server == null) {
            throw new IOException("Server is not bound: " + location);
        }
        return server.connect();
    }

    synchronized static public PipeTransportServer lookup(String name) {
        return servers.get(name);
	}

    synchronized static public Map<String, PipeTransportServer> getServers() {
   		return new HashMap<String, PipeTransportServer>(servers);
    }

    synchronized static public void unbind(PipeTransportServer server) {
    	servers.remove(server.getName());
    }
}
