/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.protocols.netty;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.james.protocols.api.ClientAuth;

/**
 * This class should be used to setup encrypted protocol handling
 */
public final class Encryption {

    private final SSLContext context;
    private final boolean starttls;
    private final String[] enabledCipherSuites;
    private final ClientAuth clientAuth;

    private Encryption(SSLContext context, boolean starttls, String[] enabledCipherSuites, ClientAuth clientAuth) {
        this.context = context;
        this.starttls = starttls;
        this.enabledCipherSuites = enabledCipherSuites;
        this.clientAuth = clientAuth;
    }

    public static Encryption createTls(SSLContext context) {
        return createTls(context, null, ClientAuth.NONE);
    }

    /**
     * Create a new {@link Encryption} which is TLS based and only allows the
     * given Ciphersuites
     *
     * @param enabledCipherSuites
     *            or <code>null</code> if all Ciphersuites should be allowed
     * @param clientAuth
     *            specifies certificate based client authentication mode
     */
    public static Encryption createTls(SSLContext context, String[] enabledCipherSuites, ClientAuth clientAuth) {
        return new Encryption(context, false, enabledCipherSuites, clientAuth);
    }

    public static Encryption createStartTls(SSLContext context) {
        return createStartTls(context, null, ClientAuth.NONE);
    }

    /**
     * Create a new {@link Encryption} which uses STARTTLS and only allows the
     * given Ciphersuites
     *
     * @param enabledCipherSuites
     *            or <code>null</code> if all Ciphersuites should be allowed
     * @param clientAuth
     *            specifies certificate based client authentication mode
     */
    public static Encryption createStartTls(SSLContext context, String[] enabledCipherSuites, ClientAuth clientAuth) {
        return new Encryption(context, true, enabledCipherSuites, clientAuth);
    }

    /**
     * Return the {@link SSLContext} to use
     * 
     * @return context
     */
    public SSLContext getContext() {
        return context;
    }

    /**
     * Return <code>true</code> if this {@link Encryption} should be used for
     * STARTTLS
     * 
     * @return starttls
     */
    public boolean isStartTLS() {
        return starttls;
    }

    /**
     * Return the Ciphersuites that are allowed for the {@link Encryption} or
     * <code>null</code> if all should be allowed
     * 
     * @return ciphersuites
     */
    public String[] getEnabledCipherSuites() {
        return enabledCipherSuites;
    }

    /**
     * Return the client authentication mode for the {@link Encryption}
     * @return authentication mode
     */
    public ClientAuth getClientAuth() {
        return clientAuth;
    }

    /**
     * Create a new {@link SSLEngine} configured according to this class.
     * @return sslengine
     */
    public SSLEngine createSSLEngine() {
        SSLEngine engine = context.createSSLEngine();

        // We need to copy the String array because of possible security issues.
        // See https://issues.apache.org/jira/browse/PROTOCOLS-18
        String[] cipherSuites = ArrayUtils.clone(enabledCipherSuites);

        if (cipherSuites != null && cipherSuites.length > 0) {
            engine.setEnabledCipherSuites(cipherSuites);
        }
        if (ClientAuth.NEED.equals(clientAuth)) {
            engine.setNeedClientAuth(true);
        }
        if (ClientAuth.WANT.equals(clientAuth)) {
            engine.setWantClientAuth(true);
        }
        return engine;
    }
}
