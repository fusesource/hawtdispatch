/**
 * Copyright (C) 2010, FuseSource Corp.  All rights reserved.
 */
package org.fusesource.hawtdispatch.transport;

import java.security.cert.X509Certificate;

/**
 * <p>
 * </p>
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public interface SecureTransport extends Transport {

    /**
     * Gets the X509Certificate associated withe the peer.
     * @return
     */
    public X509Certificate[] getPeerX509Certificates();

}
