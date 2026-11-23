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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Properties;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.transport.KeyStoreHolderTest.Identity;
import org.apache.james.transport.mailets.SMIMECheckSignature;
import org.apache.mailet.AttributeName;
import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoGeneratorBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.mail.smime.SMIMESignedGenerator;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
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

    static Identity mailSigner(String email, KeyPurposeId purpose) throws Exception {
        KeyPair keyPair = KeyStoreHolderTest.keyPair();
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(new X500Name("CN=Root"), BigInteger.valueOf(System.nanoTime()),
            new Date(System.currentTimeMillis() - 3_600_000), new Date(System.currentTimeMillis() + 3_600_000),
            new X500Name("CN=Mail signer"), keyPair.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        builder.addExtension(Extension.subjectAlternativeName, false, new GeneralNames(new GeneralName(GeneralName.rfc822Name, email)));
        builder.addExtension(Extension.extendedKeyUsage, false, new ExtendedKeyUsage(purpose));
        builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature));
        X509Certificate certificate = new JcaX509CertificateConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME)
            .getCertificate(builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(root.keyPair().getPrivate())));
        return new Identity(keyPair, certificate);
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
        Identity signer = mailSigner("signer@domain.tld", KeyPurposeId.id_kp_emailProtection);

        assertThat(status(mail(KeyStoreHolderTest.signedContent(signer, ImmutableList.of(signer.certificate(), root.certificate())), "signer@DOMAIN.tld")))
            .isEqualTo("Good signature");
    }

    @Test
    void theLocalPartShouldBeComparedIgnoringItsCase() throws Exception {
        Identity signer = mailSigner("signer@domain.tld", KeyPurposeId.id_kp_emailProtection);

        assertThat(status(mail(KeyStoreHolderTest.signedContent(signer, ImmutableList.of(signer.certificate(), root.certificate())), "Signer@domain.tld")))
            .isEqualTo("Good signature");
    }

    @Test
    void aValidSignatureFromACertificateNamingAnotherAddressShouldBeASignerMismatch() throws Exception {
        Identity signer = mailSigner("other@domain.tld", KeyPurposeId.id_kp_emailProtection);
        Mail mail = mail(KeyStoreHolderTest.signedContent(signer, ImmutableList.of(signer.certificate(), root.certificate())), "signer@domain.tld");

        assertThat(status(mail)).isEqualTo("Signer mismatch");
        assertThat(mail.attributesMap()).doesNotContainKey(AttributeName.of("org.apache.james.SMIMECheckSignature"));
    }

    @Test
    void aSignatureFromTheAuthorShouldBeGoodWhenAMailingListIsTheSender() throws Exception {
        Identity signer = mailSigner("signer@domain.tld", KeyPurposeId.id_kp_emailProtection);
        Mail mail = mail(KeyStoreHolderTest.signedContent(signer, ImmutableList.of(signer.certificate(), root.certificate())), "signer@domain.tld");
        mail.getMessage().setHeader("Sender", "list-bounces@lists.tld");
        mail.getMessage().saveChanges();

        assertThat(status(mail)).isEqualTo("Good signature");
    }

    @Test
    void aMalformedFromShouldBeASignerMismatch() throws Exception {
        Identity signer = mailSigner("signer@domain.tld", KeyPurposeId.id_kp_emailProtection);
        Mail mail = mail(KeyStoreHolderTest.signedContent(signer, ImmutableList.of(signer.certificate(), root.certificate())), "signer@domain.tld");
        mail.getMessage().setHeader("From", "\"unterminated <signer@domain.tld>");
        mail.getMessage().saveChanges();

        assertThat(status(mail)).isEqualTo("Signer mismatch");
    }

    @Test
    void severalFromFieldsShouldBeASignerMismatch() throws Exception {
        Identity signer = mailSigner("signer@domain.tld", KeyPurposeId.id_kp_emailProtection);
        Mail mail = mail(KeyStoreHolderTest.signedContent(signer, ImmutableList.of(signer.certificate(), root.certificate())), "signer@domain.tld");
        mail.getMessage().addHeader("From", "ceo@domain.tld");
        mail.getMessage().saveChanges();

        assertThat(status(mail)).isEqualTo("Signer mismatch");
    }

    @Test
    void severalSenderFieldsShouldBeASignerMismatch() throws Exception {
        Identity signer = mailSigner("signer@domain.tld", KeyPurposeId.id_kp_emailProtection);
        Mail mail = mail(KeyStoreHolderTest.signedContent(signer, ImmutableList.of(signer.certificate(), root.certificate())), "ceo@domain.tld");
        mail.getMessage().addHeader("Sender", "signer@domain.tld");
        mail.getMessage().addHeader("Sender", "ceo@domain.tld");
        mail.getMessage().saveChanges();

        assertThat(status(mail)).isEqualTo("Signer mismatch");
    }

    @Test
    void aFromFieldNameHoldingACommentShouldBeASignerMismatch() throws Exception {
        Identity signer = mailSigner("signer@domain.tld", KeyPurposeId.id_kp_emailProtection);
        Mail mail = mail(KeyStoreHolderTest.signedContent(signer, ImmutableList.of(signer.certificate(), root.certificate())), "signer@domain.tld");
        mail.getMessage().addHeader("From(x)", "ceo@domain.tld");
        mail.getMessage().saveChanges();

        assertThat(status(mail)).isEqualTo("Signer mismatch");
    }

    @Test
    void aFromReadDifferentlyByMime4jShouldBeASignerMismatch() throws Exception {
        Identity signer = mailSigner("signer@domain.tld", KeyPurposeId.id_kp_emailProtection);
        Mail mail = mail(KeyStoreHolderTest.signedContent(signer, ImmutableList.of(signer.certificate(), root.certificate())), "signer@domain.tld");
        // Jakarta Mail reads signer@domain.tld, mime4j (Email/get) signer@domain.tld.evil
        mail.getMessage().setHeader("From", "signer@domain.tld(comment).evil");
        mail.getMessage().saveChanges();

        assertThat(status(mail)).isEqualTo("Signer mismatch");
    }

    @Test
    void aCertificateWithoutTheEmailProtectionPurposeShouldNotBeTrusted() throws Exception {
        Identity signer = mailSigner("signer@domain.tld", KeyPurposeId.id_kp_serverAuth);

        assertThat(status(mail(KeyStoreHolderTest.signedContent(signer, ImmutableList.of(signer.certificate(), root.certificate())), "signer@domain.tld")))
            .isEqualTo("Bad signature");
    }

    @Test
    void anOpaqueSignatureWithoutSmimeTypeShouldBeGood() throws Exception {
        Identity signer = mailSigner("signer@domain.tld", KeyPurposeId.id_kp_emailProtection);
        SMIMESignedGenerator generator = new SMIMESignedGenerator();
        generator.addSignerInfoGenerator(new JcaSimpleSignerInfoGeneratorBuilder()
            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
            .build("SHA256withRSA", signer.keyPair().getPrivate(), signer.certificate()));
        generator.addCertificates(new JcaCertStore(ImmutableList.of(signer.certificate(), root.certificate())));
        MimeBodyPart content = new MimeBodyPart();
        content.setText("Hello");
        ByteArrayOutputStream encapsulated = new ByteArrayOutputStream();
        generator.generateEncapsulated(content).writeTo(encapsulated);
        String withoutSmimeType = encapsulated.toString(StandardCharsets.US_ASCII)
            .replaceAll(";\\s*smime-type=\"?signed-data\"?", "");
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()),
            new ByteArrayInputStream(("From: signer@domain.tld\r\n" + withoutSmimeType).getBytes(StandardCharsets.US_ASCII)));

        assertThat(message.getContentType()).doesNotContain("smime-type");
        assertThat(status(FakeMail.builder().name("mail").mimeMessage(message).build())).isEqualTo("Good signature");
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

    @Test
    void aQuotedLocalPartShouldMatchItsSigner() throws Exception {
        Identity signer = mailSigner("\"john doe\"@domain.tld", KeyPurposeId.id_kp_emailProtection);
        Mail mail = mail(KeyStoreHolderTest.signedContent(signer, ImmutableList.of(signer.certificate(), root.certificate())), "\"john doe\"@domain.tld");

        assertThat(status(mail)).isEqualTo("Good signature");
    }

    @Test
    void anUnrelatedInvalidFieldNameShouldNotBeASignerMismatch() throws Exception {
        Identity signer = mailSigner("signer@domain.tld", KeyPurposeId.id_kp_emailProtection);
        Mail mail = mail(KeyStoreHolderTest.signedContent(signer, ImmutableList.of(signer.certificate(), root.certificate())), "signer@domain.tld");
        mail.getMessage().addHeader("X Foo", "bar");
        mail.getMessage().saveChanges();

        assertThat(status(mail)).isEqualTo("Good signature");
    }

    @Test
    void aCertificateNamingNoAddressShouldBeAccepted() throws Exception {
        // RFC 8550 section 3: receivers accept certificates with no e-mail address
        Identity signer = signer("CN=Signer");
        Mail mail = mail(KeyStoreHolderTest.signedContent(signer, ImmutableList.of(signer.certificate(), root.certificate())), "signer@domain.tld");

        assertThat(status(mail)).isEqualTo("Good signature");
    }
}
