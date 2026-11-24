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

package org.apache.james.app.spring;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Set;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.io.FileHandler;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates, at first start, the keystore the IMAP servers use for TLS when it does not exist: a self-signed
 * certificate, in a file readable by its owner only. Nothing is shipped: each installation gets its own key.
 */
public class TlsKeystore {
    private static final Logger LOGGER = LoggerFactory.getLogger(TlsKeystore.class);
    private static final String CONF_PREFIX = "file://conf/";
    private static final Set<PosixFilePermission> OWNER_ONLY = Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

    /**
     * @param confDirectory the conf directory, which holds imapserver.xml and to which file://conf/ refers
     */
    public static void ensureImapKeystores(Path confDirectory) {
        Path imapServerXml = confDirectory.resolve("imapserver.xml");
        if (!Files.exists(imapServerXml)) {
            return;
        }
        try {
            XMLConfiguration configuration = new XMLConfiguration();
            new FileHandler(configuration).load(imapServerXml.toFile());
            for (HierarchicalConfiguration<ImmutableNode> server : configuration.configurationsAt("imapserver")) {
                if (usesTls(server)) {
                    ensureKeystore(confDirectory, server.getString("tls.keystore"), server.getString("tls.secret", ""),
                        server.getString("tls.keystoreType", "PKCS12"));
                }
            }
        } catch (Exception e) {
            LOGGER.error("Could not generate the TLS keystore of the IMAP servers", e);
        }
    }

    private static boolean usesTls(HierarchicalConfiguration<ImmutableNode> server) {
        return server.getBoolean("[@enabled]", true)
            && (server.getBoolean("tls[@startTLS]", false) || server.getBoolean("tls[@socketTLS]", false));
    }

    private static void ensureKeystore(Path confDirectory, String keystore, String secret, String type) throws Exception {
        if (keystore == null || !keystore.startsWith(CONF_PREFIX)) {
            return;
        }
        Path keystoreFile = confDirectory.resolve(keystore.substring(CONF_PREFIX.length()));
        if (Files.exists(keystoreFile, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        KeyStore keyStore = selfSigned(secret.toCharArray(), type);
        createOwnerOnly(keystoreFile);
        try (OutputStream out = Files.newOutputStream(keystoreFile, LinkOption.NOFOLLOW_LINKS)) {
            keyStore.store(out, secret.toCharArray());
        } catch (IOException e) {
            Files.deleteIfExists(keystoreFile);
            throw e;
        }
        LOGGER.warn("Generated a self-signed TLS keystore in {}: replace it with a certificate clients trust", keystoreFile);
    }

    private static KeyStore selfSigned(char[] password, String type) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(4096);
        KeyPair keyPair = generator.generateKeyPair();
        X500Name name = new X500Name("CN=james");
        X509Certificate certificate = new JcaX509CertificateConverter().getCertificate(new JcaX509v3CertificateBuilder(name,
            new BigInteger(64, new SecureRandom()),
            Date.from(Instant.now()),
            Date.from(Instant.now().plus(365, ChronoUnit.DAYS)),
            name, keyPair.getPublic())
            .build(new JcaContentSignerBuilder("SHA256WITHRSA").build(keyPair.getPrivate())));
        KeyStore keyStore = KeyStore.getInstance(type);
        keyStore.load(null, password);
        keyStore.setEntry("james", new KeyStore.PrivateKeyEntry(keyPair.getPrivate(), new Certificate[] {certificate}),
            new KeyStore.PasswordProtection(password));
        return keyStore;
    }

    /**
     * Readable by its owner only where the file system supports POSIX permissions. Elsewhere (Windows), the file gets
     * the access rights of its directory.
     */
    private static void createOwnerOnly(Path file) throws IOException {
        if (file.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            Files.createFile(file, PosixFilePermissions.asFileAttribute(OWNER_ONLY));
        } else {
            Files.createFile(file);
        }
    }
}
