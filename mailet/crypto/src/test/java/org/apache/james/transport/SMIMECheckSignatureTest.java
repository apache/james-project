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

package org.apache.james.transport;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Properties;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.transport.KeyStoreHolderTest.Identity;
import org.apache.james.transport.mailets.SMIMECheckSignature;
import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.bouncycastle.asn1.x500.X500Name;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.common.collect.ImmutableList;

class SMIMECheckSignatureTest {
    private static final String STATUS_HEADER = "X-SMIME-Status";
    private static final String PASSWORD = "secret";

    private static Identity root;

    private SMIMECheckSignature testee;

    @BeforeAll
    static void setUpRoot() throws Exception {
        KeyStoreHolderTest.setUpProvider();
        root = KeyStoreHolderTest.selfSigned("CN=Root");
    }

    @BeforeEach
    void setUp(@TempDir Path directory) throws Exception {
        File keyStoreFile = directory.resolve("trusted.p12").toFile();
        KeyStore keyStore = KeyStoreHolderTest.trusting(root.certificate());
        KeyStore pkcs12 = KeyStore.getInstance("PKCS12");
        pkcs12.load(null, null);
        pkcs12.setCertificateEntry("root", keyStore.getCertificate("trusted0"));
        try (OutputStream out = new FileOutputStream(keyStoreFile)) {
            pkcs12.store(out, PASSWORD.toCharArray());
        }
        testee = new SMIMECheckSignature(fileSystem(keyStoreFile));
        testee.init(FakeMailetConfig.builder()
            .mailetName("SMIMECheckSignature")
            .setProperty("keyStoreType", "PKCS12")
            .setProperty("keyStoreFileName", "file://" + keyStoreFile.getAbsolutePath())
            .setProperty("keyStorePassword", PASSWORD)
            .build());
    }

    private static FileSystem fileSystem(File keyStoreFile) {
        return new FileSystem() {
            @Override
            public InputStream getResource(String url) throws IOException {
                return new FileInputStream(keyStoreFile);
            }

            @Override
            public File getFile(String fileURL) {
                return keyStoreFile;
            }

            @Override
            public File getBasedir() {
                return keyStoreFile.getParentFile();
            }
        };
    }

    static Identity signer(String subject) throws Exception {
        return KeyStoreHolderTest.issue(subject, KeyStoreHolderTest.keyPair(), new X500Name("CN=Root"), root.keyPair(), false);
    }

    static Mail mail(MimeMultipart content, String from) throws Exception {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        message.setFrom(from);
        message.setContent(content);
        message.saveChanges();
        return FakeMail.builder()
            .name("mail")
            .mimeMessage(message)
            .build();
    }

    private String status(Mail mail) throws Exception {
        testee.service(mail);
        return mail.getMessage().getHeader(STATUS_HEADER, null);
    }

    @Test
    void aValidSignatureFromATrustedSignerShouldBeGood() throws Exception {
        Identity signer = signer("CN=Signer");

        assertThat(status(mail(KeyStoreHolderTest.signedContent(signer, ImmutableList.of(signer.certificate(), root.certificate())), "signer@domain.tld")))
            .isEqualTo("Good signature");
    }

    @Test
    void aMultipartMixedWithTwoPartsShouldNotBeSigned() throws Exception {
        MimeMultipart mixed = new MimeMultipart("mixed");
        MimeBodyPart first = new MimeBodyPart();
        first.setText("Hello");
        MimeBodyPart second = new MimeBodyPart();
        second.setText("World");
        mixed.addBodyPart(first);
        mixed.addBodyPart(second);

        assertThat(status(mail(mixed, "signer@domain.tld"))).isEqualTo("Not signed");
    }

    @Test
    void aMultipartSignedWithThreePartsShouldNotBeSigned() throws Exception {
        Identity signer = signer("CN=Signer");
        MimeMultipart signed = KeyStoreHolderTest.signedContent(signer, ImmutableList.of(signer.certificate(), root.certificate()));
        MimeBodyPart extra = new MimeBodyPart();
        extra.setText("Smuggled");
        signed.addBodyPart(extra, 0);

        assertThat(status(mail(signed, "signer@domain.tld"))).isEqualTo("Not signed");
    }
}
