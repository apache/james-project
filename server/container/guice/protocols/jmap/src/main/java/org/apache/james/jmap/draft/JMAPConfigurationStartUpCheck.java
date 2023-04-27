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

package org.apache.james.jmap.draft;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import javax.inject.Inject;

import org.apache.james.RunArguments;
import org.apache.james.RunArguments.Argument;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.jmap.draft.crypto.SecurityKeyLoader;
import org.apache.james.lifecycle.api.StartUpCheck;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;

public class JMAPConfigurationStartUpCheck implements StartUpCheck {

    private static final Logger LOGGER = LoggerFactory.getLogger(JMAPConfigurationStartUpCheck.class);
    public static final String CHECK_NAME = "JMAPConfigurationStartUpCheck";

    private final SecurityKeyLoader securityKeyLoader;
    private final JMAPDraftConfiguration jmapConfiguration;
    private final RunArguments runArguments;
    private final FileSystem fileSystem;

    @Inject
    JMAPConfigurationStartUpCheck(SecurityKeyLoader securityKeyLoader,
                                  JMAPDraftConfiguration jmapConfiguration,
                                  RunArguments runArguments,
                                  FileSystem fileSystem) {
        this.securityKeyLoader = securityKeyLoader;
        this.jmapConfiguration = jmapConfiguration;
        this.runArguments = runArguments;
        this.fileSystem = fileSystem;
    }

    @Override
    public CheckResult check() {
        if (jmapConfiguration.isEnabled()) {
            return checkSecurityKey();
        }

        return CheckResult.builder()
            .checkName(checkName())
            .resultType(ResultType.GOOD)
            .build();
    }

    private CheckResult checkSecurityKey() {
        try {
            loadSecurityKey();
            return CheckResult.builder()
                .checkName(checkName())
                .resultType(ResultType.GOOD)
                .build();
        } catch (Exception e) {
            LOGGER.error("Cannot load security key from jmap configuration", e);
            return CheckResult.builder()
                .checkName(checkName())
                .resultType(ResultType.BAD)
                .description(e.getMessage())
                .build();
        }
    }

    private void loadSecurityKey() throws Exception {
        try {
            securityKeyLoader.load();
        } catch (FileNotFoundException e) {
            if (runArguments.contain(Argument.GENERATE_KEYSTORE)) {
                LOGGER.warn("Can not load asymmetric key from configuration file" + e.getMessage());
                LOGGER.warn("James will try the auto-generate an asymmetric key. It is just for development, should not use it on production");
                generateKeystore();
            } else {
                throw e;
            }
        }
    }

    @Override
    public String checkName() {
        return CHECK_NAME;
    }

    private void generateKeystore() throws Exception {
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

        final char[] password =  jmapConfiguration.getSecret()
            .orElseGet(() -> {
                String dummyPass = new String(new byte[8]);
                LOGGER.warn("Keystore dummy password: " + dummyPass);
                return dummyPass;
            }).toCharArray();

        KeyStore keystore = KeyStore.getInstance(jmapConfiguration.getKeystoreType());
        keystore.load(null, password);

        KeyStore.PrivateKeyEntry privKeyEntry = new KeyStore.PrivateKeyEntry(keyPair.getPrivate(),
            new Certificate[] {mySelfSignedCert});
        keystore.setEntry("james", privKeyEntry, new KeyStore.PasswordProtection(password));

        storeFile(password, keystore);
    }

    private void storeFile(char[] password, KeyStore keystore) throws KeyStoreException, NoSuchAlgorithmException, CertificateException {
        File keystoreFile = jmapConfiguration.getKeystore()
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
