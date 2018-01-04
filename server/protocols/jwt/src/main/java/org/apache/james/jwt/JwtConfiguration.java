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

package org.apache.james.jwt;

import java.util.Optional;

import com.google.common.base.Preconditions;

public class JwtConfiguration {
    private static final boolean DEFAULT_VALUE = true;
    private final Optional<String> jwtPublicKeyPem;

    public JwtConfiguration(Optional<String> jwtPublicKeyPem) {
        Preconditions.checkState(validPublicKey(jwtPublicKeyPem), "The provided public key is not valid");
        this.jwtPublicKeyPem = jwtPublicKeyPem;
    }

    private boolean validPublicKey(Optional<String> jwtPublicKeyPem) {
        PublicKeyReader reader = new PublicKeyReader();
        return jwtPublicKeyPem.map(value -> reader.fromPEM(Optional.of(value)).isPresent())
            .orElse(DEFAULT_VALUE);
    }

    public Optional<String> getJwtPublicKeyPem() {
        return jwtPublicKeyPem;
    }
}
