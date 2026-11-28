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

package org.apache.james.oidc.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.james.core.Username;
import org.apache.james.oidc.OidcTokenCacheConfiguration;
import org.apache.james.oidc.OidcTokenCacheContract;
import org.apache.james.oidc.Token;
import org.apache.james.oidc.TokenInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CaffeineOidcTokenCacheTest extends OidcTokenCacheContract {
    private CaffeineOidcTokenCache testee;

    @BeforeEach
    void setUp() {
        testee = new CaffeineOidcTokenCache(tokenInfoResolver, OidcTokenCacheConfiguration.DEFAULT);
    }

    @Override
    public CaffeineOidcTokenCache testee() {
        return testee;
    }

    @Override
    public Optional<Username> getUsernameFromCache(Token token) {
        return testee.getUsernameFromCache(token);
    }

    @Test
    void invalidateShouldDropATokenReloadedBeforeItsEvictionWasNotified() {
        AtomicLong nanos = new AtomicLong();
        Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();
        CaffeineOidcTokenCache cache = new CaffeineOidcTokenCache(tokenInfoResolver, OidcTokenCacheConfiguration.DEFAULT, nanos::get, tasks::add);

        CompletableFuture<TokenInfo> firstRequest = cache.associatedInformation(token).toFuture();
        runAll(tasks);
        assertThat(firstRequest.join()).isEqualTo(TOKEN_INFO);

        // The token expires, and a request reloads it before the removal listener of the expired entry runs:
        // the listener then unindexes the reloaded token.
        nanos.addAndGet(OidcTokenCacheConfiguration.DEFAULT.expiration().plus(Duration.ofSeconds(1)).toNanos());
        CompletableFuture<TokenInfo> reload = cache.associatedInformation(token).toFuture();
        runAll(tasks);
        assertThat(reload.join()).isEqualTo(TOKEN_INFO);
        verify(tokenInfoResolver, times(2)).apply(token);

        cache.invalidate(SID).block();
        CompletableFuture<TokenInfo> afterLogout = cache.associatedInformation(token).toFuture();
        runAll(tasks);

        assertThat(afterLogout).isCompletedExceptionally();
        assertThat(cache.getUsernameFromCache(token)).isEmpty();
    }

    private static void runAll(Queue<Runnable> tasks) {
        Runnable task;
        while ((task = tasks.poll()) != null) {
            task.run();
        }
    }
}
