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

import com.google.common.annotations.VisibleForTesting;

import io.netty.handler.ssl.SslHandler;

/**
 * This class should be used to setup encrypted protocol handling
 */
public interface Encryption {

    interface Factory {
        Encryption create() throws Exception;
    }

    @VisibleForTesting
    static Encryption createTls(SSLContext context) {
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
    static Encryption createTls(SSLContext context, String[] enabledCipherSuites, ClientAuth clientAuth) {
        return new Encryption.LegacyJavaEncryption(context, false, enabledCipherSuites, clientAuth);
    }

    @VisibleForTesting
    static Encryption createStartTls(SSLContext context) {
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
    static Encryption createStartTls(SSLContext context, String[] enabledCipherSuites, ClientAuth clientAuth) {
        return new Encryption.LegacyJavaEncryption(context, true, enabledCipherSuites, clientAuth);
    }

    /**
     * Return <code>true</code> if this {@link Encryption} should be used for
     * STARTTLS
     *
     * @return starttls
     */
    boolean isStartTLS();

    boolean supportsEncryption();

    /**
     * Return the Ciphersuites that are allowed for the {@link Encryption} or
     * <code>null</code> if all should be allowed
     *
     * @return ciphersuites
     */
    String[] getEnabledCipherSuites();

    /**
     * Return the client authentication mode for the {@link Encryption}
     *
     * @return authentication mode
     */
    ClientAuth getClientAuth();

    SslHandler sslHandler();

    class LegacyJavaEncryption implements Encryption {
        private final SSLContext context;
        private final boolean starttls;
        private final String[] enabledCipherSuites;
        private final ClientAuth clientAuth;

        private LegacyJavaEncryption(SSLContext context, boolean starttls, String[] enabledCipherSuites, ClientAuth clientAuth) {
            this.context = context;
            this.starttls = starttls;
            this.enabledCipherSuites = enabledCipherSuites;
            this.clientAuth = clientAuth;
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

        public boolean supportsEncryption() {
            return context != null;
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
         *
         * @return authentication mode
         */
        public ClientAuth getClientAuth() {
            return clientAuth;
        }

        /**
         * Create a new {@link SSLEngine} configured according to this class.
         *
         * @return sslengine
         */
        private SSLEngine createSSLEngine() {
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

        public SslHandler sslHandler() {
            SSLEngine engine = createSSLEngine();
            // We need to set clientMode to false.
            // See https://issues.apache.org/jira/browse/JAMES-1025
            engine.setUseClientMode(false);
            return new SslHandler(engine);
        }
    }
}
