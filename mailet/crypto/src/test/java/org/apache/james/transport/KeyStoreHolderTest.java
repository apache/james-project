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

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMultipart;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoGeneratorBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.mail.smime.SMIMESigned;
import org.bouncycastle.mail.smime.SMIMESignedGenerator;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

class KeyStoreHolderTest {
    private static final AtomicLong SERIAL = new AtomicLong(1);

    record Identity(KeyPair keyPair, X509Certificate certificate) {
    }

    @BeforeAll
    static void setUpProvider() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    static KeyPair keyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    static Identity issue(String subject, KeyPair subjectKeyPair, X500Name issuer, KeyPair issuerKeyPair, boolean ca) throws Exception {
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(issuer, BigInteger.valueOf(SERIAL.getAndIncrement()),
            new Date(System.currentTimeMillis() - 3_600_000), new Date(System.currentTimeMillis() + 3_600_000),
            new X500Name(subject), subjectKeyPair.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(ca));
        X509Certificate certificate = new JcaX509CertificateConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME)
            .getCertificate(builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(issuerKeyPair.getPrivate())));
        return new Identity(subjectKeyPair, certificate);
    }

    static Identity selfSigned(String subject) throws Exception {
        KeyPair keyPair = keyPair();
        return issue(subject, keyPair, new X500Name(subject), keyPair, true);
    }

    static SMIMESigned sign(Identity signer, List<X509Certificate> attachedCertificates) throws Exception {
        SMIMESignedGenerator generator = new SMIMESignedGenerator();
        generator.addSignerInfoGenerator(new JcaSimpleSignerInfoGeneratorBuilder()
            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
            .build("SHA256withRSA", signer.keyPair().getPrivate(), signer.certificate()));
        generator.addCertificates(new JcaCertStore(attachedCertificates));
        MimeBodyPart content = new MimeBodyPart();
        content.setText("Hello");
        MimeMultipart multipart = generator.generate(content);
        return new SMIMESigned(multipart);
    }

    static KeyStore trusting(X509Certificate... certificates) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        for (int i = 0; i < certificates.length; i++) {
            keyStore.setCertificateEntry("trusted" + i, certificates[i]);
        }
        return keyStore;
    }

    @Test
    void verifySignaturesShouldOnlyConsiderTheCertificateNamedByTheSignerId() throws Exception {
        Identity root = selfSigned("CN=Root");
        Identity signer = issue("CN=Signer", keyPair(), new X500Name("CN=Root"), root.keyPair(), false);
        // Another certificate for the signer key: it verifies the signature, but the SID does not name it
        Identity alias = issue("CN=Alias", signer.keyPair(), new X500Name("CN=Root"), root.keyPair(), false);
        ImmutableList.Builder<X509Certificate> attached = ImmutableList.<X509Certificate>builder()
            .add(alias.certificate())
            .add(signer.certificate())
            .add(root.certificate());
        IntStream.range(0, 5).forEach(i -> {
            try {
                attached.add(selfSigned("CN=Unrelated " + i).certificate());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        List<SMIMESignerInfo> signerInfos = new KeyStoreHolder(trusting(root.certificate()))
            .verifySignatures(sign(signer, attached.build()));

        assertThat(signerInfos).singleElement()
            .satisfies(info -> {
                assertThat(info.getSignerCertificate()).isEqualTo(signer.certificate());
                assertThat(info.isSignValid()).isTrue();
                assertThat(info.getCertPath()).isNotNull();
            });
    }

    @Test
    void verifySignaturesShouldNotTrustAnUntrustedSigner() throws Exception {
        Identity root = selfSigned("CN=Root");
        Identity signer = issue("CN=Signer", keyPair(), new X500Name("CN=Root"), root.keyPair(), false);

        List<SMIMESignerInfo> signerInfos = new KeyStoreHolder(trusting(selfSigned("CN=Other root").certificate()))
            .verifySignatures(sign(signer, ImmutableList.of(signer.certificate(), root.certificate())));

        assertThat(signerInfos).singleElement()
            .satisfies(info -> {
                assertThat(info.isSignValid()).isTrue();
                assertThat(info.getCertPath()).isNull();
            });
    }
}
