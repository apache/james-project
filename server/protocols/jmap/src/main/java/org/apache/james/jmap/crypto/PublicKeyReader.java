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

import java.io.IOException;
import java.io.StringReader;
import java.security.interfaces.RSAPublicKey;
import java.util.Optional;

import org.bouncycastle.openssl.PEMReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PublicKeyReader {

    private static final Logger LOGGER = LoggerFactory.getLogger(PublicKeyReader.class);

    Optional<RSAPublicKey> fromPEM(Optional<String> pemKey) {

        return pemKey
                .map(k -> new PEMReader(new StringReader(k)))
                .flatMap(this::publicKeyFrom);
    }

    private Optional<RSAPublicKey> publicKeyFrom(PEMReader reader) {
        try {
            Object readPEM = reader.readObject();
            RSAPublicKey rsaPublicKey = null;
            if (readPEM instanceof RSAPublicKey) {
                rsaPublicKey = (RSAPublicKey) readPEM;
            }
            return Optional.ofNullable(rsaPublicKey);
        } catch (IOException e) {
            LOGGER.warn("Error when reading the PEM file", e);
            return Optional.empty();
        }
    }
}