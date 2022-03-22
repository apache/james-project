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

package org.apache.james.protocols.api;

import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.apache.commons.lang3.ArrayUtils;

import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.ssl.JdkSslContext;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;

/**
 * This class should be used to setup encrypted protocol handling
 * 
 * It's recommended to use Netty {@link SslContext} in preference to the JDK's {@link SSLContext}.
 * Netty {@link SslContext} is much easier to configure, use and supports more features.
 * 
 * Netty {@link SslContext} can be configured to use Google's BoringSSL.
 * BoringSSL is a clone of OpenSSL with all redundant/old ciphers removed and is compiled into to all the Chrome/Chromium browsers.
 * Simply include the 'io.netty:netty-tcnative-boringssl-static' dependency as discussed here: https://netty.io/wiki/forked-tomcat-native.html
 * You should regularly update the version of this library to keep in-sync with the latest version used by Chrome/Chromium browsers.
 * 
 * Use {@link SslContextBuilder} to create an instance of {@link SslContext}.
 */
public abstract class Encryption {

    private final boolean starttls;
    
    private Encryption(boolean starttls) {
        this.starttls = starttls;
    }

    public static Encryption createTls(SslContext context) {
        return new EncryptionNetty(context, false);
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
        return new EncryptionJDK(context, false, enabledCipherSuites, clientAuth);
    }

    public static Encryption createStartTls(SslContext context) {
        return new EncryptionNetty(context, true);
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
        return new EncryptionJDK(context, true, enabledCipherSuites, clientAuth);
    }

    /**
     * Return the {@link SSLContext} to use
     * 
     * @return context
     */
    public abstract SSLContext getContext();

    /**
     * Return <code>true</code> if this {@link Encryption} should be used for
     * STARTTLS
     * 
     * @return starttls
     */
    public final boolean isStartTLS() {
        return starttls;
    }

    /**
     * Return the Ciphersuites that are allowed for the {@link Encryption} or
     * <code>null</code> if all should be allowed
     * 
     * @return ciphersuites
     */
    public abstract String[] getEnabledCipherSuites();

    /**
     * Return the client authentication mode for the {@link Encryption}
     * @return authentication mode
     */
    public abstract ClientAuth getClientAuth();

    /**
     * Create a new {@link SSLEngine} configured according to this class.
     * @return sslengine
     */
    public abstract SSLEngine createSSLEngine();

    private static final class EncryptionJDK extends Encryption {
        private final SSLContext context;
        private final String[] enabledCipherSuites;
        private final ClientAuth clientAuth;

        private EncryptionJDK(SSLContext context, boolean starttls, String[] enabledCipherSuites, ClientAuth clientAuth) {
            super(starttls);
            this.context = context;
            this.enabledCipherSuites = enabledCipherSuites;
            this.clientAuth = clientAuth;
        }

        @Override
        public SSLContext getContext() {
            return context;
        }

        @Override
        public String[] getEnabledCipherSuites() {
            return enabledCipherSuites;
        }

        @Override
        public ClientAuth getClientAuth() {
            return clientAuth;
        }

        @Override
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

    private static final class EncryptionNetty extends Encryption {
        private final SslContext context;

        private EncryptionNetty(SslContext context, boolean starttls) {
            super(starttls);
            this.context = context;
        }

        @Override
        public SSLContext getContext() {
            if (context instanceof JdkSslContext) {
                return ((JdkSslContext) context).context();
            }
            throw new IllegalStateException("Not supported");
        }

        @Override
        public String[] getEnabledCipherSuites() {
            List<String> ciphers = context.cipherSuites();
            return ciphers.toArray(new String[ciphers.size()]);
        }

        @Override
        public ClientAuth getClientAuth() {
            throw new IllegalStateException("Not supported");
        }

        @Override
        public SSLEngine createSSLEngine() {
            return context.newEngine(ByteBufAllocator.DEFAULT);
        }
    }
    
}
