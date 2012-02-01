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

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.concurrent.Executor;
import java.security.NoSuchAlgorithmException;

/**
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */

public class SslTransportServer extends TcpTransportServer {

    public static SslTransportServer createTransportServer(URI uri) throws Exception {
        SslTransportServer rc = new SslTransportServer(uri);
        rc.setSSLContext(SSLContext.getInstance(SslTransport.protocol(uri.getScheme())));
        return rc;
    }

    protected KeyManager[] keyManagers;
    private TrustManager[] trustManagers;
    protected String protocol = "TLS";
    protected SSLContext sslContext;
    protected Executor blockingExecutor;

    public SslTransportServer(URI location) throws UnknownHostException {
        super(location);
    }

    public void setKeyManagers(KeyManager[] keyManagers) {
        this.keyManagers = keyManagers;
    }
    public void setTrustManagers(TrustManager[] trustManagers) {
        this.trustManagers = trustManagers;
    }

    public void start(Runnable onCompleted) throws Exception {
        if( keyManagers!=null ) {
            sslContext.init(keyManagers, trustManagers, null);
        } else {
            sslContext = SSLContext.getDefault();
        }
        super.start(onCompleted);
    }

    protected TcpTransport createTransport() {
        SslTransport rc = new SslTransport();
        rc.setSSLContext(sslContext);
        rc.setBlockingExecutor(blockingExecutor);
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

    public Executor getBlockingExecutor() {
        return blockingExecutor;
    }

    public void setBlockingExecutor(Executor blockingExecutor) {
        this.blockingExecutor = blockingExecutor;
    }

}
