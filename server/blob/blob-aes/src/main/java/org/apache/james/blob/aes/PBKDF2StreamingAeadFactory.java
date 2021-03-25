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

package org.apache.james.blob.aes;

import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import com.google.crypto.tink.subtle.AesGcmHkdfStreaming;

public class PBKDF2StreamingAeadFactory {
    private static final int PBKDF2_ITERATIONS = 65536;
    private static final int KEY_SIZE = 256;
    private static final String SECRET_KEY_FACTORY_ALGORITHM = "PBKDF2WithHmacSHA1";
    private static final String HKDF_ALGO = "HmacSha256";
    private static final int KEY_SIZE_IN_BYTES = 32;
    private static final int SEGMENT_SIZE = 4096;
    private static final int OFFSET = 0;
    public static final byte[] EMPTY_ASSOCIATED_DATA = new byte[0];

    public static AesGcmHkdfStreaming newAesGcmHkdfStreaming(CryptoConfig config) {
        try {
            SecretKey secretKey = deriveKey(config);
            return new AesGcmHkdfStreaming(secretKey.getEncoded(), HKDF_ALGO, KEY_SIZE_IN_BYTES, SEGMENT_SIZE, OFFSET);
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Incorrect crypto setup", e);

        }
    }

    private static SecretKey deriveKey(CryptoConfig cryptoConfig)
        throws NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] saltBytes = cryptoConfig.salt();
        SecretKeyFactory skf = SecretKeyFactory.getInstance(SECRET_KEY_FACTORY_ALGORITHM);
        PBEKeySpec spec = new PBEKeySpec(cryptoConfig.password(), saltBytes, PBKDF2_ITERATIONS, KEY_SIZE);
        return skf.generateSecret(spec);
    }
}