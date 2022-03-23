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

import java.util.Optional;

import javax.net.ssl.SSLContext;
import javax.net.ssl.X509ExtendedKeyManager;

import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.protocols.netty.Encryption;

import nl.altindag.ssl.SSLFactory;
import nl.altindag.ssl.util.PemUtils;

public class LegacyJavaEncryptionFactory implements Encryption.Factory {
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
            sslFactoryBuilder.withTrustMaterial(
                fileSystem.getFile(sslConfig.getTruststore()).toPath(),
                sslConfig.getTruststoreSecret(),
                sslConfig.getKeystoreType());
        }

        SSLContext context = sslFactoryBuilder.build().getSslContext();

        if (sslConfig.useStartTLS()) {
            return Encryption.createStartTls(context, sslConfig.getEnabledCipherSuites(), sslConfig.getClientAuth());
        } else {
           return Encryption.createTls(context, sslConfig.getEnabledCipherSuites(), sslConfig.getClientAuth());
        }
    }
}
