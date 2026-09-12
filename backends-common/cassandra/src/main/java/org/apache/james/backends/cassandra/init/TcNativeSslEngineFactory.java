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

package org.apache.james.backends.cassandra.init;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.util.Optional;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;

import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverExecutionProfile;
import com.datastax.oss.driver.api.core.context.DriverContext;
import com.datastax.oss.driver.api.core.metadata.EndPoint;
import com.datastax.oss.driver.api.core.ssl.SslEngineFactory;
import com.datastax.oss.driver.internal.core.ssl.DefaultSslEngineFactory;

import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.ssl.IdentityCipherSuiteFilter;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;

/**
 * BoringSSL (Netty tcnative) counterpart of the driver's {@link DefaultSslEngineFactory}, reading the same
 * {@code advanced.ssl-engine-factory} options.
 *
 * <p>Keystore reloading ({@code keystore-reload-interval}) is not supported.
 *
 * <p>Requires {@code netty-tcnative-boringssl-static} on the runtime classpath.
 */
public class TcNativeSslEngineFactory implements SslEngineFactory {
    public static boolean supports(DriverExecutionProfile profile) {
        return profile.isDefined(DefaultDriverOption.SSL_ENGINE_FACTORY_CLASS)
            && isDefaultSslEngineFactory(profile.getString(DefaultDriverOption.SSL_ENGINE_FACTORY_CLASS))
            && !profile.isDefined(DefaultDriverOption.SSL_KEYSTORE_RELOAD_INTERVAL);
    }

    private static boolean isDefaultSslEngineFactory(String className) {
        return className.equals(DefaultSslEngineFactory.class.getSimpleName())
            || className.equals(DefaultSslEngineFactory.class.getName());
    }

    private final SslContext sslContext;
    private final boolean requireHostnameValidation;
    private final boolean allowDnsReverseLookupSan;

    public TcNativeSslEngineFactory(DriverContext driverContext) {
        this(driverContext.getConfig().getDefaultProfile());
    }

    public TcNativeSslEngineFactory(DriverExecutionProfile profile) {
        OpenSsl.ensureAvailability();
        try {
            this.sslContext = buildContext(profile);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot initialize SSL Context", e);
        }
        this.requireHostnameValidation = profile.getBoolean(DefaultDriverOption.SSL_HOSTNAME_VALIDATION, true);
        this.allowDnsReverseLookupSan = profile.getBoolean(DefaultDriverOption.SSL_ALLOW_DNS_REVERSE_LOOKUP_SAN, true);
    }

    private static SslContext buildContext(DriverExecutionProfile profile) throws Exception {
        SslContextBuilder builder = SslContextBuilder.forClient()
            .sslProvider(SslProvider.OPENSSL);

        Optional<KeyStore> trustStore = loadStore(profile, DefaultDriverOption.SSL_TRUSTSTORE_PATH, DefaultDriverOption.SSL_TRUSTSTORE_PASSWORD);
        if (trustStore.isPresent()) {
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore.get());
            builder.trustManager(trustManagerFactory);
        }

        Optional<KeyStore> keyStore = loadStore(profile, DefaultDriverOption.SSL_KEYSTORE_PATH, DefaultDriverOption.SSL_KEYSTORE_PASSWORD);
        if (keyStore.isPresent()) {
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore.get(), password(profile, DefaultDriverOption.SSL_KEYSTORE_PASSWORD));
            builder.keyManager(keyManagerFactory);
        }

        if (profile.isDefined(DefaultDriverOption.SSL_CIPHER_SUITES)) {
            // IdentityCipherSuiteFilter: see TCNativeEncryptionFactory, the default filter strips TLS 1.3 suites
            builder.ciphers(profile.getStringList(DefaultDriverOption.SSL_CIPHER_SUITES), IdentityCipherSuiteFilter.INSTANCE);
        }
        return builder.build();
    }

    private static Optional<KeyStore> loadStore(DriverExecutionProfile profile, DefaultDriverOption pathOption, DefaultDriverOption passwordOption) throws Exception {
        if (!profile.isDefined(pathOption)) {
            return Optional.empty();
        }
        try (InputStream inputStream = Files.newInputStream(Paths.get(profile.getString(pathOption)))) {
            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(inputStream, password(profile, passwordOption));
            return Optional.of(keyStore);
        }
    }

    private static char[] password(DriverExecutionProfile profile, DefaultDriverOption passwordOption) {
        if (profile.isDefined(passwordOption)) {
            return profile.getString(passwordOption).toCharArray();
        }
        return null;
    }

    @Override
    public SSLEngine newSslEngine(EndPoint remoteEndpoint) {
        SSLEngine engine = createEngine(remoteEndpoint.resolve());
        if (requireHostnameValidation) {
            SSLParameters parameters = engine.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            engine.setSSLParameters(parameters);
        }
        return engine;
    }

    private SSLEngine createEngine(SocketAddress remoteAddress) {
        if (remoteAddress instanceof InetSocketAddress inetAddress) {
            return sslContext.newEngine(ByteBufAllocator.DEFAULT, hostname(inetAddress), inetAddress.getPort());
        }
        return sslContext.newEngine(ByteBufAllocator.DEFAULT);
    }

    private String hostname(InetSocketAddress address) {
        if (allowDnsReverseLookupSan) {
            return address.getHostName();
        }
        return address.getHostString();
    }

    @Override
    public void close() {
        // SslProvider.OPENSSL contexts and engines are released by the GC: nothing to close, and the
        // factory stays usable when ClusterFactory reuses its session builder
    }
}
