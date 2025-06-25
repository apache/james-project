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

import java.net.URL;
import java.security.PublicKey;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkException;
import com.auth0.jwk.SigningKeyNotFoundException;
import com.auth0.jwk.UrlJwkProvider;
import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class JwksPublicKeyProvider implements PublicKeyProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(JwksPublicKeyProvider.class);

    public static JwksPublicKeyProvider of(URL jwksURL) {
        return new JwksPublicKeyProvider(jwksURL);
    }

    private final UrlJwkProvider jwkProvider;

    private JwksPublicKeyProvider(URL jwksURL) {
        Preconditions.checkNotNull(jwksURL);
        this.jwkProvider = new UrlJwkProvider(jwksURL);
    }

    @Override
    public List<PublicKey> get() throws MissingOrInvalidKeyException {
        return getAllFromProvider();
    }

    @Override
    public Optional<PublicKey> get(String kid) throws MissingOrInvalidKeyException {
        try {
            return Optional.of(jwkProvider.get(kid).getPublicKey());
        } catch (SigningKeyNotFoundException notFoundException) {
            return Optional.empty();
        } catch (JwkException e) {
            LOGGER.error("Can't get publicKeys has kid = {} from jwksURL.", kid, e);
            throw new MissingOrInvalidKeyException();
        }
    }

    private List<PublicKey> getAllFromProvider() throws MissingOrInvalidKeyException  {
        try {
            return jwkProvider.getAll()
                .stream()
                .map(Throwing.function(Jwk::getPublicKey))
                .collect(ImmutableList.toImmutableList());
        } catch (JwkException e) {
            LOGGER.error("Can't get publicKeys from jwksURL.", e);
            throw new MissingOrInvalidKeyException();
        }
    }
}
