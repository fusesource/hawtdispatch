/**
 * Copyright (C) 2010-2011, FuseSource Corp.  All rights reserved.
 *
 *     http://fusesource.com
 *
 * The software in this package is published under the terms of the
 * CDDL license a copy of which has been included with this distribution
 * in the license.txt file.
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
