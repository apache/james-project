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

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Base64;

import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

public class JamesSignatureHandler implements SignatureHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(JamesSignatureHandler.class);

    public static final String ALGORITHM = "SHA1withRSA";

    private final SecurityKeyLoader keyLoader;

    private AsymmetricKeys securityKeys;


    @Inject
    @VisibleForTesting JamesSignatureHandler(SecurityKeyLoader keyLoader) {
        this.keyLoader = keyLoader;
    }

    @Override
    public void init() throws Exception {
        securityKeys = keyLoader.load();
    }

    @Override
    public String sign(String source) {
        Preconditions.checkNotNull(source);
        try {
            Signature javaSignature = Signature.getInstance(ALGORITHM);
            javaSignature.initSign(securityKeys.getPrivateKey());
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
            javaSignature.initVerify(securityKeys.getPublicKey());
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
