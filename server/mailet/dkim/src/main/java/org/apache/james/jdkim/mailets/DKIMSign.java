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

package org.apache.james.jdkim.mailets;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Security;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.mail.Header;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.jdkim.DKIMSigner;
import org.apache.james.jdkim.api.BodyHasher;
import org.apache.james.jdkim.api.Headers;
import org.apache.james.jdkim.api.SignatureRecord;
import org.apache.james.jdkim.exceptions.PermFailException;
import org.apache.james.server.core.MimeMessageInputStream;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMDecryptorProvider;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder;

import com.github.fge.lambdas.Throwing;

/**
 * This mailet sign a message using the DKIM protocol
 * If the privateKey is encoded using a password then you can pass
 * the password as privateKeyPassword parameter.
 *
 * Sample configuration with inlined private key:
 *
 * <pre><code>
 * &lt;mailet match=&quot;All&quot; class=&quot;DKIMSign&quot;&gt;
 *   &lt;signatureTemplate&gt;v=1; s=selector; d=example.com; h=from:to:received:received; a=rsa-sha256; bh=; b=;&lt;/signatureTemplate&gt;
 *   &lt;privateKey&gt;
 *   -----BEGIN RSA PRIVATE KEY-----
 *   MIICXAIBAAKBgQDYDaYKXzwVYwqWbLhmuJ66aTAN8wmDR+rfHE8HfnkSOax0oIoT
 *   M5zquZrTLo30870YMfYzxwfB6j/Nz3QdwrUD/t0YMYJiUKyWJnCKfZXHJBJ+yfRH
 *   r7oW+UW3cVo9CG2bBfIxsInwYe175g9UjyntJpWueqdEIo1c2bhv9Mp66QIDAQAB
 *   AoGBAI8XcwnZi0Sq5N89wF+gFNhnREFo3rsJDaCY8iqHdA5DDlnr3abb/yhipw0I
 *   /1HlgC6fIG2oexXOXFWl+USgqRt1kTt9jXhVFExg8mNko2UelAwFtsl8CRjVcYQO
 *   cedeH/WM/mXjg2wUqqZenBmlKlD6vNb70jFJeVaDJ/7n7j8BAkEA9NkH2D4Zgj/I
 *   OAVYccZYH74+VgO0e7VkUjQk9wtJ2j6cGqJ6Pfj0roVIMUWzoBb8YfErR8l6JnVQ
 *   bfy83gJeiQJBAOHk3ow7JjAn8XuOyZx24KcTaYWKUkAQfRWYDFFOYQF4KV9xLSEt
 *   ycY0kjsdxGKDudWcsATllFzXDCQF6DTNIWECQEA52ePwTjKrVnLTfCLEG4OgHKvl
 *   Zud4amthwDyJWoMEH2ChNB2je1N4JLrABOE+hk+OuoKnKAKEjWd8f3Jg/rkCQHj8
 *   mQmogHqYWikgP/FSZl518jV48Tao3iXbqvU9Mo2T6yzYNCCqIoDLFWseNVnCTZ0Q
 *   b+IfiEf1UeZVV5o4J+ECQDatNnS3V9qYUKjj/krNRD/U0+7eh8S2ylLqD3RlSn9K
 *   tYGRMgAtUXtiOEizBH6bd/orzI9V9sw8yBz+ZqIH25Q=
 *   -----END RSA PRIVATE KEY-----
 *   &lt;/privateKey&gt;
 * &lt;/mailet&gt;
 * </code></pre>
 *
 * Sample configuration with file-provided private key:
 *
 * <pre><code>
 * &lt;mailet match=&quot;All&quot; class=&quot;DKIMSign&quot;&gt;
 *   &lt;signatureTemplate&gt;v=1; s=selector; d=example.com; h=from:to:received:received; a=rsa-sha256; bh=; b=;&lt;/signatureTemplate&gt;
 *   &lt;privateKeyFilepath&gt;conf://dkim-signing.pem&lt;/privateKeyFilepath&gt;
 * &lt;/mailet&gt;
 * </code></pre>
 *
 * By default the mailet assume that Javamail will convert LF to CRLF when sending
 * so will compute the hash using converted newlines. If you don't want this
 * behaviour then set forceCRLF attribute to false.
 */
public class DKIMSign extends GenericMailet {

    private final FileSystem fileSystem;
    private String signatureTemplate;
    private PrivateKey privateKey;
    private boolean forceCRLF;

    @Inject
    public DKIMSign(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    /**
     * @return the signatureTemplate
     */
    private String getSignatureTemplate() {
        return signatureTemplate;
    }

    /**
     * @return the privateKey
     */
    private PrivateKey getPrivateKey() {
        return privateKey;
    }

    public void init() throws MessagingException {
        signatureTemplate = getInitParameter("signatureTemplate");
        Optional<String> privateKeyPassword = getInitParameterAsOptional("privateKeyPassword");
        forceCRLF = getInitParameter("forceCRLF", true);

        try {
            char[] passphrase = privateKeyPassword.map(String::toCharArray).orElse(null);
            InputStream pem = getInitParameterAsOptional("privateKey")
                .map(String::getBytes)
                .map(ByteArrayInputStream::new)
                .map(byteArrayInputStream -> (InputStream) byteArrayInputStream)
                .orElseGet(Throwing.supplier(() -> fileSystem.getResource(getInitParameter("privateKeyFilepath"))).sneakyThrow());

            privateKey = extractPrivateKey(pem, passphrase);
        } catch (NoSuchAlgorithmException e) {
            throw new MessagingException("Unknown private key algorythm: " + e.getMessage(), e);
        } catch (InvalidKeySpecException e) {
            throw new MessagingException("PrivateKey should be in base64 encoded PKCS8 (der) format: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new MessagingException("Problem during reading: " + e.getMessage(), e);
        }
    }

    public void service(Mail mail) throws MessagingException {
        DKIMSigner signer = new DKIMSigner(getSignatureTemplate(), getPrivateKey());
        SignatureRecord signRecord = signer
                .newSignatureRecordTemplate(getSignatureTemplate());
        try {
            BodyHasher bhj = signer.newBodyHasher(signRecord);
            MimeMessage message = mail.getMessage();
            Headers headers = new MimeMessageHeaders(message);
            try {
                OutputStream os = new HeaderSkippingOutputStream(bhj.getOutputStream());
                if (forceCRLF) {
                    os = new CRLFOutputStream(os);
                }
                try (MimeMessageInputStream stream = new MimeMessageInputStream(message)) {
                    stream.transferTo(os);
                }
            } catch (IOException e) {
                throw new MessagingException("Exception calculating bodyhash: " + e.getMessage(), e);
            } finally {
                try {
                    bhj.getOutputStream().close();
                } catch (IOException e) {
                    throw new MessagingException("Exception calculating bodyhash: " + e.getMessage(), e);
                }
            }
            String signatureHeader = signer.sign(headers, bhj);
            // Unfortunately JavaMail does not give us a method to add headers
            // on top.
            // message.addHeaderLine(signatureHeader);
            prependHeader(message, signatureHeader);
        } catch (PermFailException e) {
            throw new MessagingException("PermFail while signing: " + e.getMessage(), e);
        }

    }

    private void prependHeader(MimeMessage message, String signatureHeader)
            throws MessagingException {
        List<String> prevHeader = Collections.list(message.getAllHeaderLines());
        Collections.list(message.getAllHeaders())
            .stream()
            .map(Header::getName)
            .forEach(Throwing.consumer(message::removeHeader).sneakyThrow());

        message.addHeaderLine(signatureHeader);
        prevHeader
            .forEach(Throwing.consumer(message::addHeaderLine).sneakyThrow());
    }

    private PrivateKey extractPrivateKey(InputStream rawKey, char[] passphrase) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());

        try (InputStreamReader pemReader = new InputStreamReader(rawKey)) {
            try (PEMParser pemParser = new PEMParser(pemReader)) {
                Object pemObject = pemParser.readObject();
                JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
                KeyPair keyPair;
                if (pemObject instanceof PrivateKeyInfo) {
                    return converter.getPrivateKey((PrivateKeyInfo)pemObject);
                }
                if (pemObject instanceof PEMEncryptedKeyPair) {
                    PEMEncryptedKeyPair pemEncryptedKeyPair = (PEMEncryptedKeyPair) pemObject;
                    PEMDecryptorProvider decProv = new JcePEMDecryptorProviderBuilder().build(passphrase);
                    keyPair = converter.getKeyPair(pemEncryptedKeyPair.decryptKeyPair(decProv));
                } else {
                    keyPair = converter.getKeyPair((PEMKeyPair) pemObject);
                }

                KeyFactory keyFac = KeyFactory.getInstance("RSA");
                RSAPrivateCrtKeySpec privateKeySpec = keyFac.getKeySpec(keyPair.getPrivate(), RSAPrivateCrtKeySpec.class);

                return keyFac.generatePrivate(privateKeySpec);
            }
        }
    }
}
