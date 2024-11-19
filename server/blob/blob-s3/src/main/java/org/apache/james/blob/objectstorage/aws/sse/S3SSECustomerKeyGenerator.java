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

package org.apache.james.blob.objectstorage.aws.sse;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import com.google.common.hash.Hashing;

public class S3SSECustomerKeyGenerator {

    public static final int ITERATION_COUNT = 65536;
    public static final int KEY_LENGTH = 256;

    private final SecretKeyFactory secretKeyFactory;

    public S3SSECustomerKeyGenerator() throws NoSuchAlgorithmException {
        this(S3SSECConfiguration.CUSTOMER_KEY_FACTORY_ALGORITHM_DEFAULT);
    }

    public S3SSECustomerKeyGenerator(String customerKeyFactoryAlgorithm) throws NoSuchAlgorithmException {
        this.secretKeyFactory = SecretKeyFactory.getInstance(customerKeyFactoryAlgorithm);
    }

    public String generateCustomerKey(String masterPassword, String salt) throws InvalidKeySpecException {
        PBEKeySpec spec = new PBEKeySpec(masterPassword.toCharArray(), salt.getBytes(), ITERATION_COUNT, KEY_LENGTH);
        byte[] derivedKey = secretKeyFactory.generateSecret(spec).getEncoded();
        return Base64.getEncoder().encodeToString(derivedKey);
    }

    public String generateCustomerKeyMd5(String customerKey) {
        return Base64.getEncoder().encodeToString(Hashing.md5().hashBytes(Base64.getDecoder().decode(customerKey)).asBytes());
    }
}