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

package org.apache.james.protocols.lib;

import java.time.Duration;
import java.util.Arrays;

import jakarta.inject.Inject;

import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.protocols.api.ClientAuth;
import org.apache.james.protocols.netty.Encryption;
import org.apache.james.protocols.netty.SslConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslProvider;

/**
 * {@link Encryption.Factory} that delegates to BoringSSL via Netty's tcnative for raw TLS connections
 * (PEM-based, no clientAuth, no STARTTLS).
 *
 * <p>Falls back to {@link LegacyJavaEncryptionFactory} when any of the following apply:
 * <ul>
 *   <li>Client certificate authentication is required</li>
 *   <li>A keystore (JKS/PKCS12) is used instead of PEM files</li>
 * </ul>
 *
 * <p>Requires {@code netty-tcnative-boringssl-static} on the runtime classpath.
 */
public class TCNativeEncryptionFactory implements Encryption.Factory {
    private static final Logger LOGGER = LoggerFactory.getLogger(TCNativeEncryptionFactory.class);

    private final FileSystem fileSystem;
    private final LegacyJavaEncryptionFactory legacyFactory;

    @Inject
    public TCNativeEncryptionFactory(FileSystem fileSystem, LegacyJavaEncryptionFactory legacyFactory) {
        this.fileSystem = fileSystem;
        this.legacyFactory = legacyFactory;
    }

    @Override
    public Encryption create(SslConfig conf) throws Exception {
        if (shouldDelegate(conf)) {
            LOGGER.debug("TCNative: delegating to legacy JCA (clientAuth={}, keystore={})",
                conf.getClientAuth(), conf.getKeystore() != null);
            return legacyFactory.create(conf);
        }
        LOGGER.debug("TCNative: using BoringSSL for raw TLS with PEM certs");
        return createNative(conf);
    }

    private boolean shouldDelegate(SslConfig conf) {
        return conf.getClientAuth() != ClientAuth.NONE
            || conf.getKeystore() != null
            || conf.getTruststore() != null;
    }

    private Encryption createNative(SslConfig conf) throws Exception {
        SslContextBuilder builder = SslContextBuilder.forServer(
                fileSystem.getResource(conf.getCertificates()),
                fileSystem.getResource(conf.getPrivateKey()),
                conf.getSecret())
            .sslProvider(SslProvider.OPENSSL);

        if (conf.getEnabledCipherSuites() != null && conf.getEnabledCipherSuites().length > 0) {
            builder.ciphers(Arrays.asList(conf.getEnabledCipherSuites()));
        }
        if (conf.getEnabledProtocols() != null && conf.getEnabledProtocols().length > 0) {
            builder.protocols(conf.getEnabledProtocols());
        }
        conf.getSessionCacheSize().ifPresent(builder::sessionCacheSize);
        conf.getSessionCacheTimeout()
            .map(Duration::getSeconds)
            .map(Math::toIntExact)
            .ifPresent(builder::sessionTimeout);

        return new NettySslContextEncryption(builder.build(), conf.useStartTLS(), conf.getEnabledCipherSuites());
    }

    private static class NettySslContextEncryption implements Encryption {
        private final SslContext sslContext;
        private final boolean startTLS;
        private final String[] enabledCipherSuites;

        private NettySslContextEncryption(SslContext sslContext, boolean startTLS, String[] enabledCipherSuites) {
            this.sslContext = sslContext;
            this.startTLS = startTLS;
            this.enabledCipherSuites = enabledCipherSuites;
        }

        @Override
        public boolean isStartTLS() {
            return startTLS;
        }

        @Override
        public boolean supportsEncryption() {
            return true;
        }

        @Override
        public String[] getEnabledCipherSuites() {
            return enabledCipherSuites;
        }

        @Override
        public ClientAuth getClientAuth() {
            return ClientAuth.NONE;
        }

        @Override
        public SslHandler sslHandler() {
            return sslContext.newHandler(ByteBufAllocator.DEFAULT);
        }
    }
}
