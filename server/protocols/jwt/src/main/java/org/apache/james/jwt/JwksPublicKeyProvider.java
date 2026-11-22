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
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkException;
import com.auth0.jwk.UrlJwkProvider;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableList;

/**
 * Retrieves public keys from a JWKS endpoint.
 *
 * The key set is cached: a token is usually verified before authentication, so fetching the key set for each
 * token would let any client make James query the identity provider at will. The key set is refreshed when it is
 * older than {@link #CACHE_TTL}, or when an unknown kid is requested, but never more than once per
 * {@link #MIN_REFRESH_INTERVAL}. When a refresh fails, the last known key set is kept. While a refresh is
 * running, the other callers use the last known key set instead of waiting for it.
 */
public class JwksPublicKeyProvider implements PublicKeyProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(JwksPublicKeyProvider.class);

    @VisibleForTesting
    static final Duration CACHE_TTL = Duration.ofMinutes(10);
    @VisibleForTesting
    static final Duration MIN_REFRESH_INTERVAL = Duration.ofSeconds(30);
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 5000;

    public static JwksPublicKeyProvider of(URL jwksURL) {
        return new JwksPublicKeyProvider(jwksURL, Ticker.systemTicker());
    }

    private record Key(Optional<String> kid, PublicKey publicKey) {
    }

    private record KeySet(List<Key> keys, long fetchedAtNanos) {
        List<PublicKey> publicKeys() {
            return keys.stream()
                .map(Key::publicKey)
                .collect(ImmutableList.toImmutableList());
        }

        Optional<PublicKey> find(String kid) {
            return keys.stream()
                .filter(key -> key.kid().equals(Optional.ofNullable(kid)))
                .map(Key::publicKey)
                .findFirst();
        }
    }

    private final UrlJwkProvider jwkProvider;
    private final Ticker ticker;
    private final ReentrantLock refreshLock = new ReentrantLock();
    private volatile KeySet keySet;
    private Long lastFetchAttemptNanos; // guarded by refreshLock

    @VisibleForTesting
    JwksPublicKeyProvider(URL jwksURL, Ticker ticker) {
        Preconditions.checkNotNull(jwksURL);
        this.jwkProvider = new UrlJwkProvider(jwksURL, CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
        this.ticker = ticker;
    }

    @Override
    public List<PublicKey> get() throws MissingOrInvalidKeyException {
        return keySet(false).publicKeys();
    }

    @Override
    public Optional<PublicKey> get(String kid) throws MissingOrInvalidKeyException {
        return keySet(false).find(kid)
            .or(() -> keySet(true).find(kid));
    }

    private KeySet keySet(boolean refresh) throws MissingOrInvalidKeyException {
        KeySet current = keySet;
        if (!refresh && isFresh(current)) {
            return current;
        }
        if (current != null) {
            if (!refreshLock.tryLock()) {
                // Another caller is refreshing the key set: do not wait for it
                return current;
            }
        } else {
            refreshLock.lock();
        }
        try {
            current = keySet;
            if (!refresh && isFresh(current)) {
                return current;
            }
            long now = ticker.read();
            if (lastFetchAttemptNanos != null && now - lastFetchAttemptNanos < MIN_REFRESH_INTERVAL.toNanos()) {
                // The key set was fetched (or a fetch failed) very recently: do not query the identity provider again
                return keySetOrFail(current);
            }
            lastFetchAttemptNanos = now;
            try {
                KeySet fetched = new KeySet(toKeys(jwkProvider.getAll()), now);
                keySet = fetched;
                return fetched;
            } catch (JwkException | RuntimeException e) {
                LOGGER.error("Can't get publicKeys from jwksURL.", e);
                return keySetOrFail(current);
            }
        } finally {
            refreshLock.unlock();
        }
    }

    private List<Key> toKeys(List<Jwk> jwks) {
        return jwks.stream()
            .flatMap(jwk -> {
                try {
                    return Stream.of(new Key(Optional.ofNullable(jwk.getId()), jwk.getPublicKey()));
                } catch (JwkException | RuntimeException e) {
                    LOGGER.warn("Ignoring the key {} of the jwksURL: it can not be converted to a public key", jwk.getId(), e);
                    return Stream.empty();
                }
            })
            .collect(ImmutableList.toImmutableList());
    }

    private boolean isFresh(KeySet current) {
        return current != null && ticker.read() - current.fetchedAtNanos() < CACHE_TTL.toNanos();
    }

    private KeySet keySetOrFail(KeySet current) throws MissingOrInvalidKeyException {
        if (current == null) {
            throw new MissingOrInvalidKeyException();
        }
        return current;
    }
}
