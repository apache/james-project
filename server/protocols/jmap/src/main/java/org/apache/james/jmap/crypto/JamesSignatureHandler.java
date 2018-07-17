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

package org.apache.james.jmap.crypto;

import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.util.Base64;
import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.jmap.JMAPConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

public class JamesSignatureHandler implements SignatureHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(JamesSignatureHandler.class);

    public static final String ALIAS = "james";
    public static final String ALGORITHM = "SHA1withRSA";
    public static final String JKS = "JKS";
    
    private final FileSystem fileSystem;
    private final JMAPConfiguration jmapConfiguration;

    private PrivateKey privateKey;
    private PublicKey publicKey;


    @Inject
    @VisibleForTesting JamesSignatureHandler(FileSystem fileSystem, JMAPConfiguration jmapConfiguration) {
        this.fileSystem = fileSystem;
        this.jmapConfiguration = jmapConfiguration;
    }

    @Override
    public void init() throws Exception {
        KeyStore keystore = KeyStore.getInstance(JKS);
        InputStream fis = fileSystem.getResource(jmapConfiguration.getKeystore());
        char[] secret = jmapConfiguration.getSecret().toCharArray();
        keystore.load(fis, secret);
        Certificate aliasCertificate = Optional
                .ofNullable(keystore.getCertificate(ALIAS))
                .orElseThrow(() -> new KeyStoreException("Alias '" + ALIAS + "' keystore can't be found"));

        publicKey = aliasCertificate.getPublicKey();
        Key key = keystore.getKey(ALIAS, secret);
        if (! (key instanceof PrivateKey)) {
            throw new KeyStoreException("Provided key is not a PrivateKey");
        }
        privateKey = (PrivateKey) key;
    }

    @Override
    public String sign(String source) {
        Preconditions.checkNotNull(source);
        try {
            Signature javaSignature = Signature.getInstance(ALGORITHM);
            javaSignature.initSign(privateKey);
            javaSignature.update(source.getBytes());
            return Base64.getEncoder().encodeToString(javaSignature.sign());
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean verify(String source, String signature) {
        Preconditions.checkNotNull(source);
        Preconditions.checkNotNull(signature);
        try {
            Signature javaSignature = Signature.getInstance(ALGORITHM);
            javaSignature.initVerify(publicKey);
            javaSignature.update(source.getBytes());
            return javaSignature.verify(Base64.getDecoder().decode(signature));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException(e);
        } catch (SignatureException e) {
            LOGGER.warn("Attempt to use a malformed signature '{}' for source '{}'", signature, source, e);
            return false;
        }
    }
}
