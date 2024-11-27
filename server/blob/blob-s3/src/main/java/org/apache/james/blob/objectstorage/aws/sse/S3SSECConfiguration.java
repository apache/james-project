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

import java.util.List;
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;

import com.google.common.base.Preconditions;

public interface S3SSECConfiguration {

    String ENCRYPTION_S3_SSEC_ALGORITHM_DEFAULT = "AES256";
    String CUSTOMER_KEY_FACTORY_ALGORITHM_DEFAULT = "PBKDF2WithHmacSHA256";
    List<String> SUPPORTED_ALGORITHMS = List.of(ENCRYPTION_S3_SSEC_ALGORITHM_DEFAULT);
    String ENCRYPTION_S3_SSEC_MASTER_KEY_ALGORITHM_PROPERTY = "encryption.s3.sse.c.master.key.algorithm";
    String ENCRYPTION_S3_SSEC_MASTER_KEY_SALT_PROPERTY = "encryption.s3.sse.c.master.key.salt";
    String ENCRYPTION_S3_SSEC_MASTER_KEY_PASSWORD_PROPERTY = "encryption.s3.sse.c.master.key.password";

    static S3SSECConfiguration from(Configuration configuration) {
        String algorithm = configuration.getString(ENCRYPTION_S3_SSEC_MASTER_KEY_ALGORITHM_PROPERTY, ENCRYPTION_S3_SSEC_ALGORITHM_DEFAULT);
        return new Basic(algorithm,
            configuration.getString(ENCRYPTION_S3_SSEC_MASTER_KEY_PASSWORD_PROPERTY, null),
            configuration.getString(ENCRYPTION_S3_SSEC_MASTER_KEY_SALT_PROPERTY, null));
    }

    String ssecAlgorithm();

    default Optional<String> customerKeyFactoryAlgorithm() {
        return Optional.of(CUSTOMER_KEY_FACTORY_ALGORITHM_DEFAULT);
    }

    record Basic(String ssecAlgorithm,
                 String masterPassword,
                 String salt) implements S3SSECConfiguration {
        public Basic {
            Preconditions.checkArgument(SUPPORTED_ALGORITHMS.contains(ssecAlgorithm), "Unsupported algorithm: " + ssecAlgorithm + ". The supported algorithms are: " + SUPPORTED_ALGORITHMS);
            Preconditions.checkNotNull(masterPassword, ENCRYPTION_S3_SSEC_MASTER_KEY_PASSWORD_PROPERTY + " cannot be null");
            Preconditions.checkNotNull(salt, ENCRYPTION_S3_SSEC_MASTER_KEY_SALT_PROPERTY + " cannot be null");
        }
    }
}
