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

import java.security.NoSuchAlgorithmException;
import java.security.cert.CertPathBuilder;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.PKIXRevocationChecker;
import java.security.cert.X509CertSelector;
import java.util.EnumSet;
import java.util.Optional;

import javax.net.ssl.CertPathTrustManagerParameters;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509ExtendedKeyManager;

import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.protocols.lib.netty.AbstractConfigurableAsyncServer;
import org.apache.james.protocols.netty.Encryption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;

import nl.altindag.ssl.SSLFactory;
import nl.altindag.ssl.pem.util.PemUtils;
import nl.altindag.ssl.trustmanager.trustoptions.TrustStoreTrustOptions;

public class LegacyJavaEncryptionFactory implements Encryption.Factory {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractConfigurableAsyncServer.class);

    private final FileSystem fileSystem;
    private final SslConfig sslConfig;

    public LegacyJavaEncryptionFactory(FileSystem fileSystem, SslConfig sslConfig) {
        this.fileSystem = fileSystem;
        this.sslConfig = sslConfig;
    }

    @Override
    public Encryption create() throws Exception {
        SSLFactory.Builder sslFactoryBuilder = SSLFactory.builder()
                .withSslContextAlgorithm("TLS");
        if (sslConfig.getKeystore() != null) {
            char[] passwordAsCharArray = Optional.ofNullable(sslConfig.getSecret())
                    .orElse("")
                    .toCharArray();
            LOGGER.debug("Building SSL config for keystore({}) at {}", sslConfig.getKeystoreType(), fileSystem.getFile(sslConfig.getKeystore()).toPath().toAbsolutePath());
            sslFactoryBuilder.withIdentityMaterial(
                    fileSystem.getFile(sslConfig.getKeystore()).toPath(),
                    passwordAsCharArray,
                    passwordAsCharArray,
                    sslConfig.getKeystoreType());
        } else {
            X509ExtendedKeyManager keyManager = PemUtils.loadIdentityMaterial(
                    fileSystem.getResource(sslConfig.getCertificates()),
                    fileSystem.getResource(sslConfig.getPrivateKey()),
                    Optional.ofNullable(sslConfig.getSecret())
                            .map(String::toCharArray)
                            .orElse(null));

            sslFactoryBuilder.withIdentityMaterial(keyManager);
        }

        if (sslConfig.getClientAuth() != null && sslConfig.getTruststore() != null) {
            Optional<TrustStoreTrustOptions<? extends CertPathTrustManagerParameters>> maybeTrustOptions = clientAuthTrustOptions(sslConfig);

            maybeTrustOptions.ifPresentOrElse(Throwing.<TrustStoreTrustOptions<? extends CertPathTrustManagerParameters>>consumer(trustOptions ->
            sslFactoryBuilder.withTrustMaterial(
                    fileSystem.getFile(sslConfig.getTruststore()).toPath(),
                    sslConfig.getTruststoreSecret(),
                    sslConfig.getKeystoreType(),
                    trustOptions)).sneakyThrow(),
                Throwing.runnable(() -> sslFactoryBuilder.withTrustMaterial(
                        fileSystem.getFile(sslConfig.getTruststore()).toPath(),
                        sslConfig.getTruststoreSecret(),
                        sslConfig.getTruststoreType())));
        }

        SSLContext context = sslFactoryBuilder.build().getSslContext();

        if (sslConfig.useStartTLS()) {
            return Encryption.createStartTls(context, sslConfig.getEnabledCipherSuites(), sslConfig.getEnabledProtocols(), sslConfig.getClientAuth());
        } else {
           return Encryption.createTls(context, sslConfig.getEnabledCipherSuites(), sslConfig.getEnabledProtocols(), sslConfig.getClientAuth());
        }
    }

    // CF https://github.com/Hakky54/sslcontext-kickstart#loading-trust-material-with-trustmanager-and-ocsp-options
    private Optional<TrustStoreTrustOptions<? extends CertPathTrustManagerParameters>> clientAuthTrustOptions(SslConfig sslConfig) throws NoSuchAlgorithmException {
        if (!sslConfig.ocspCRLChecksEnabled()) {
            return Optional.empty();
        }
        CertPathBuilder certPathBuilder = CertPathBuilder.getInstance("PKIX");
        PKIXRevocationChecker revocationChecker = (PKIXRevocationChecker) certPathBuilder.getRevocationChecker();
        revocationChecker.setOptions(EnumSet.of(PKIXRevocationChecker.Option.NO_FALLBACK));

        return Optional.of(trustStore -> {
            PKIXBuilderParameters pkixBuilderParameters = new PKIXBuilderParameters(trustStore, new X509CertSelector());
            pkixBuilderParameters.addCertPathChecker(revocationChecker);
            return new CertPathTrustManagerParameters(pkixBuilderParameters);
        });
    }
}
