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

import java.util.List;

import com.google.common.base.Preconditions;

public class JwtConfiguration {
    private final List<String> jwtPublicKeyPem;

    public JwtConfiguration(List<String> jwtPublicKeyPem) {
        Preconditions.checkState(validPublicKey(jwtPublicKeyPem), "One of the provided public key is not valid");
        this.jwtPublicKeyPem = jwtPublicKeyPem;
    }

    private boolean validPublicKey(List<String> jwtPublicKeyPem) {
        PublicKeyReader reader = new PublicKeyReader();
        return jwtPublicKeyPem.stream().allMatch(value -> reader.fromPEM(value).isPresent());
    }

    public List<String> getJwtPublicKeyPem() {
        return jwtPublicKeyPem;
    }
}
