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

package org.apache.james.mailbox.inmemory.quota;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.CurrentQuotas;
import org.apache.james.mailbox.model.QuotaOperation;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.CurrentQuotaManager;
import org.apache.james.mailbox.store.quota.CurrentQuotaCalculator;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class InMemoryCurrentQuotaManager implements CurrentQuotaManager {

    private final LoadingCache<QuotaRoot, AtomicReference<CurrentQuotas>> quotaCache;

    @Inject
    public InMemoryCurrentQuotaManager(CurrentQuotaCalculator quotaCalculator, SessionProvider sessionProvider) {
        this.quotaCache = CacheBuilder.newBuilder().build(new CacheLoader<>() {
            @Override
            public AtomicReference<CurrentQuotas> load(QuotaRoot quotaRoot) {
                return new AtomicReference<>(
                    loadQuotas(quotaRoot, quotaCalculator, sessionProvider));
            }
        });
    }

    public CurrentQuotas loadQuotas(QuotaRoot quotaRoot, CurrentQuotaCalculator quotaCalculator, SessionProvider sessionProvider) {
        return quotaCalculator.recalculateCurrentQuotas(quotaRoot, sessionProvider.createSystemSession(Username.of(quotaRoot.getValue())))
            .block();
    }

    @Override
    public Mono<Void> increase(QuotaOperation quotaOperation) {
        return updateQuota(quotaOperation.quotaRoot(), quota -> quota.increase(new CurrentQuotas(quotaOperation.count(), quotaOperation.size())));
    }

    @Override
    public Mono<Void> decrease(QuotaOperation quotaOperation) {
        return updateQuota(quotaOperation.quotaRoot(), quota -> quota.decrease(new CurrentQuotas(quotaOperation.count(), quotaOperation.size())));
    }

    @Override
    public Mono<QuotaCountUsage> getCurrentMessageCount(QuotaRoot quotaRoot) {
        return Mono.fromCallable(() -> quotaCache.get(quotaRoot).get().count())
            .onErrorMap(this::wrapAsMailboxException);
    }

    @Override
    public Mono<QuotaSizeUsage> getCurrentStorage(QuotaRoot quotaRoot) {
        return Mono.fromCallable(() -> quotaCache.get(quotaRoot).get().size())
            .onErrorMap(this::wrapAsMailboxException);
    }

    @Override
    public Mono<CurrentQuotas> getCurrentQuotas(QuotaRoot quotaRoot) {
        return Mono.fromCallable(() -> quotaCache.get(quotaRoot).get())
            .subscribeOn(Schedulers.boundedElastic())
            .onErrorMap(this::wrapAsMailboxException);
    }

    @Override
    public Mono<Void> setCurrentQuotas(QuotaOperation quotaOperation) {
        return Mono.fromRunnable(() -> quotaCache.put(quotaOperation.quotaRoot(), new AtomicReference<>(new CurrentQuotas(quotaOperation.count(), quotaOperation.size()))))
            .onErrorMap(this::wrapAsMailboxException)
            .then();
    }

    private Mono<Void> updateQuota(QuotaRoot quotaRoot, UnaryOperator<CurrentQuotas> quotaFunction) {
        return Mono.fromCallable(() -> quotaCache.get(quotaRoot).updateAndGet(quotaFunction))
            .onErrorMap(this::wrapAsMailboxException)
            .then();
    }

    private Throwable wrapAsMailboxException(Throwable throwable) {
        return new MailboxException("Exception caught", throwable);
    }
}
