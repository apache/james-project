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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;

import javax.inject.Inject;

import org.apache.commons.io.IOUtils;
import org.apache.james.RunArguments;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.jmap.draft.JMAPDraftConfiguration;
import org.apache.james.jwt.PublicKeyReader;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import nl.altindag.ssl.exception.CertificateParseException;
import nl.altindag.ssl.util.KeyStoreUtils;
import nl.altindag.ssl.util.PemUtils;

public class SecurityKeyLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityKeyLoader.class);
    private static final String ALIAS = "james";

    private final FileSystem fileSystem;
    private final JMAPDraftConfiguration jmapDraftConfiguration;

    private final RunArguments runArguments;

    @VisibleForTesting
    @Inject
    SecurityKeyLoader(FileSystem fileSystem,
                      JMAPDraftConfiguration jmapDraftConfiguration,
                      RunArguments runArguments) {
        this.fileSystem = fileSystem;
        this.jmapDraftConfiguration = jmapDraftConfiguration;
        this.runArguments = runArguments;
    }

    SecurityKeyLoader(FileSystem fileSystem,
                      JMAPDraftConfiguration jmapDraftConfiguration) {
        this.fileSystem = fileSystem;
        this.jmapDraftConfiguration = jmapDraftConfiguration;
        this.runArguments = RunArguments.empty();
    }

    public AsymmetricKeys load() throws Exception {
        Preconditions.checkState(jmapDraftConfiguration.isEnabled(), "JMAP is not enabled");

        try {
            LOGGER.info("tung keystore" + jmapDraftConfiguration.getKeystore());
            if (jmapDraftConfiguration.getKeystore().isPresent()) {
                return loadFromKeystore();
            }
            return loadFromPEM();
        } catch (FileNotFoundException e) {
            if (runArguments.containStartDev()) {
                LOGGER.warn("Can not load asymmetric key from configuration file" + e.getMessage());
                LOGGER.warn("James will try the auto-generate an asymmetric key. It is just for development, should not use it on production");
                return generateAsymmetricKeys();
            }
            throw e;
        }
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

    private AsymmetricKeys generateAsymmetricKeys() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(4096);
        KeyPair keyPair = kpg.generateKeyPair();

        X500Name subjName = new X500Name("CN=james");
        X509CertificateHolder x509CertificateHolder = new JcaX509v3CertificateBuilder(subjName,
            new BigInteger(64, new SecureRandom()),
            Date.from(Instant.now()),
            Date.from(Instant.now().plus(365, ChronoUnit.DAYS)),
            subjName, keyPair.getPublic())
            .build(new JcaContentSignerBuilder("SHA256WITHRSA")
                .build(keyPair.getPrivate()));

        X509Certificate mySelfSignedCert = new JcaX509CertificateConverter().getCertificate(
            x509CertificateHolder);

        final char[] password =  jmapDraftConfiguration.getSecret()
            .orElseGet(() -> {
                String dummyPass = new String(new byte[8]);
                LOGGER.warn("Keystore dummy password: " + dummyPass);
                return dummyPass;
            }).toCharArray();

        KeyStore keystore = KeyStore.getInstance(jmapDraftConfiguration.getKeystoreType());
        keystore.load(null, password);

        KeyStore.PrivateKeyEntry privKeyEntry = new KeyStore.PrivateKeyEntry(keyPair.getPrivate(),
            new Certificate[] {mySelfSignedCert});
        keystore.setEntry(ALIAS, privKeyEntry, new KeyStore.PasswordProtection(password));

        storeFile(password, keystore);

        return new AsymmetricKeys(keyPair.getPrivate(), keyPair.getPublic());
    }

    private void storeFile(char[] password, KeyStore keystore) throws KeyStoreException, NoSuchAlgorithmException, CertificateException {
        File keystoreFile = jmapDraftConfiguration.getKeystore()
            .map(Throwing.function(fileSystem::getFile))
            .orElseThrow(() -> new IllegalArgumentException("tls.keystoreURL was not defined"));

        if (!keystoreFile.exists()) {
            try (OutputStream outputStream = new FileOutputStream(keystoreFile)) {
                keystore.store(outputStream, password);
                LOGGER.info("Generated keystore file: " + keystoreFile.getPath());
            } catch (IOException e) {
                throw new RuntimeException("Error when creating Keystore file: " + keystoreFile.getPath(), e);
            }
        }
    }

}
