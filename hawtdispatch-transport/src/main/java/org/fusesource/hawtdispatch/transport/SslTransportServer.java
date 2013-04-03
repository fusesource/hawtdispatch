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

import org.fusesource.hawtdispatch.Task;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.net.URI;
import java.security.NoSuchAlgorithmException;

/**
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */

public class SslTransportServer extends TcpTransportServer {

    public static SslTransportServer createTransportServer(URI uri) throws Exception {
        return new SslTransportServer(uri);
    }

    protected KeyManager[] keyManagers;
    private TrustManager[] trustManagers;
    protected String protocol = "TLS";
    protected SSLContext sslContext;
    private String clientAuth = "want";
    private String disabledCypherSuites = null;

    public SslTransportServer(URI location) throws Exception {
        super(location);
        setSSLContext(SSLContext.getInstance(SslTransport.protocol(location.getScheme())));
    }

    public void setKeyManagers(KeyManager[] keyManagers) {
        this.keyManagers = keyManagers;
    }
    public void setTrustManagers(TrustManager[] trustManagers) {
        this.trustManagers = trustManagers;
    }

    public void start(Task onCompleted) throws Exception {
        if( keyManagers!=null ) {
            sslContext.init(keyManagers, trustManagers, null);
        } else {
            sslContext = SSLContext.getDefault();
        }
        super.start(onCompleted);
    }

    protected TcpTransport createTransport() {
        SslTransport rc = new SslTransport();
        rc.setDispatchQueue(dispatchQueue);
        rc.setBlockingExecutor(blockingExecutor);
        rc.setSSLContext(sslContext);
        rc.setClientAuth(clientAuth);
        rc.setDisabledCypherSuites(disabledCypherSuites);
        return rc;
    }

    public SslTransportServer protocol(String value) throws NoSuchAlgorithmException {
        this.protocol = value;
        sslContext = SSLContext.getInstance(protocol);
        return this;
    }

    public SSLContext getSSLContext() {
        return sslContext;
    }

    public void setSSLContext(SSLContext sslContext) {
        this.sslContext = sslContext;
    }

    public String getClientAuth() {
        return clientAuth;
    }

    public void setClientAuth(String clientAuth) {
        this.clientAuth = clientAuth;
    }

    public String getDisabledCypherSuites() {
        return disabledCypherSuites;
    }

    public void setDisabledCypherSuites(String disabledCypherSuites) {
        this.disabledCypherSuites = disabledCypherSuites;
    }
}
