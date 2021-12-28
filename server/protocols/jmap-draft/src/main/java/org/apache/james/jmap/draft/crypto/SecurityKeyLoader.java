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

package org.apache.james.jmap.draft.crypto;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Optional;

import javax.inject.Inject;

import org.apache.commons.io.IOUtils;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.jmap.draft.JMAPDraftConfiguration;
import org.apache.james.jwt.PublicKeyReader;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import nl.altindag.ssl.exception.CertificateParseException;
import nl.altindag.ssl.util.KeyStoreUtils;
import nl.altindag.ssl.util.PemUtils;

public class SecurityKeyLoader {
    private static final String ALIAS = "james";

    private final FileSystem fileSystem;
    private final JMAPDraftConfiguration jmapDraftConfiguration;

    @VisibleForTesting
    @Inject
    SecurityKeyLoader(FileSystem fileSystem, JMAPDraftConfiguration jmapDraftConfiguration) {
        this.fileSystem = fileSystem;
        this.jmapDraftConfiguration = jmapDraftConfiguration;
    }

    public AsymmetricKeys load() throws Exception {
        Preconditions.checkState(jmapDraftConfiguration.isEnabled(), "JMAP is not enabled");

        if (jmapDraftConfiguration.getKeystore().isPresent()) {
            return loadFromKeystore();
        }
        return loadFromPEM();
    }

    private AsymmetricKeys loadFromKeystore() throws Exception {
        Preconditions.checkState(jmapDraftConfiguration.getKeystore().isPresent());
        Preconditions.checkState(jmapDraftConfiguration.getSecret().isPresent());

        char[] secret = jmapDraftConfiguration.getSecret().get().toCharArray();
        KeyStore keystore = KeyStoreUtils.loadKeyStore(fileSystem.getResource(jmapDraftConfiguration.getKeystore().get()), secret);

        Certificate aliasCertificate = Optional
            .ofNullable(keystore.getCertificate(ALIAS))
            .orElseThrow(() -> new KeyStoreException("Alias '" + ALIAS + "' keystore can't be found"));

        PublicKey publicKey = aliasCertificate.getPublicKey();
        Key key = keystore.getKey(ALIAS, secret);
        if (! (key instanceof PrivateKey)) {
            throw new KeyStoreException("Provided key is not a PrivateKey");
        }
        return new AsymmetricKeys((PrivateKey) key, publicKey);
    }

    private AsymmetricKeys loadFromPEM() throws Exception {
        Preconditions.checkState(jmapDraftConfiguration.getCertificates().isPresent());
        Preconditions.checkState(jmapDraftConfiguration.getPrivateKey().isPresent());

        PrivateKey privateKey = PemUtils.loadPrivateKey(
            fileSystem.getResource(jmapDraftConfiguration.getPrivateKey().get()),
            jmapDraftConfiguration.getSecret()
                .map(String::toCharArray)
                .orElse(null));

        return new AsymmetricKeys(privateKey, loadPublicKey());
    }

    private PublicKey loadPublicKey() throws IOException {
        try {
            X509Certificate certificate = PemUtils.loadCertificate(
                fileSystem.getResource(jmapDraftConfiguration.getCertificates().get()))
                .get(0);
            return certificate.getPublicKey();
        } catch (CertificateParseException e) {
            String publicKeyAsString = IOUtils.toString(fileSystem.getResource(jmapDraftConfiguration.getCertificates().get()), StandardCharsets.US_ASCII);
            return new PublicKeyReader()
                .fromPEM(publicKeyAsString)
                .orElseThrow(() -> new IllegalArgumentException("Key must either be a valid certificate or a public key"));
        }
    }
}
