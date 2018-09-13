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

package org.apache.james.modules.objectstorage;

import org.apache.commons.configuration.Configuration;
import org.apache.james.blob.objectstorage.AESPayloadCodec;
import org.apache.james.blob.objectstorage.DefaultPayloadCodec;
import org.apache.james.blob.objectstorage.PayloadCodec;
import org.apache.james.blob.objectstorage.crypto.CryptoConfig;

import com.amazonaws.util.StringUtils;
import com.google.common.base.Preconditions;

public enum PayloadCodecs {
    DEFAULT {
        @Override
        public PayloadCodec codec(Configuration configuration) {
            return new DefaultPayloadCodec();
        }
    },
    AES256 {
        @Override
        public PayloadCodec codec(Configuration configuration) {
            String salt = configuration.getString(OBJECTSTORAGE_AES256_HEXSALT);
            String password = configuration.getString(OBJECTSTORAGE_AES256_PASSWORD);
            Preconditions.checkArgument(!StringUtils.isNullOrEmpty(salt),
                OBJECTSTORAGE_AES256_HEXSALT + " is a " +
                    "mandatory configuration value");
            Preconditions.checkArgument(!StringUtils.isNullOrEmpty(password),
                OBJECTSTORAGE_AES256_PASSWORD + " is a " +
                    "mandatory configuration value");
            return new AESPayloadCodec(new CryptoConfig(salt, password.toCharArray()));
        }
    };

    private static final String OBJECTSTORAGE_AES256_HEXSALT = "objectstorage.aes256.hexsalt";
    private static final String OBJECTSTORAGE_AES256_PASSWORD = "objectstorage.aes256.password";

    public abstract PayloadCodec codec(Configuration configuration);


}
