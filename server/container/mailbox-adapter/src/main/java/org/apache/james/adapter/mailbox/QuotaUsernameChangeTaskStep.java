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

package org.apache.james.adapter.mailbox;

import java.time.Instant;
import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.events.EventBus;
import org.apache.james.events.RegistrationKey;
import org.apache.james.mailbox.model.CurrentQuotas;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaOperation;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.CurrentQuotaManager;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.UserQuotaRootResolver;
import org.apache.james.mailbox.store.event.EventFactory;
import org.apache.james.user.api.UsernameChangeTaskStep;
import org.reactivestreams.Publisher;

import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Mono;

public class QuotaUsernameChangeTaskStep implements UsernameChangeTaskStep {
    private static final ImmutableSet<RegistrationKey> NO_REGISTRATION_KEYS = ImmutableSet.of();
    private final QuotaManager quotaManager;
    private final MaxQuotaManager maxQuotaManager;
    private final CurrentQuotaManager currentQuotaManager;
    private final UserQuotaRootResolver userQuotaRootResolver;
    private final EventBus eventBus;

    @Inject
    public QuotaUsernameChangeTaskStep(QuotaManager quotaManager,
                                       CurrentQuotaManager currentQuotaManager,
                                       UserQuotaRootResolver userQuotaRootResolver,
                                       MaxQuotaManager maxQuotaManager,
                                       EventBus eventBus) {
        this.quotaManager = quotaManager;
        this.currentQuotaManager = currentQuotaManager;
        this.userQuotaRootResolver = userQuotaRootResolver;
        this.maxQuotaManager = maxQuotaManager;
        this.eventBus = eventBus;
    }

    @Override
    public StepName name() {
        return new StepName("QuotaUsernameChangeTaskStep");
    }

    @Override
    public int priority() {
        return 3;
    }

    @Override
    public Publisher<Void> changeUsername(Username oldUsername, Username newUsername) {
        return Mono.from(quotaManager.getQuotasReactive(userQuotaRootResolver.forUser(oldUsername)))
            .flatMap(quotas -> Mono.fromCallable(() -> userQuotaRootResolver.forUser(newUsername))
                .flatMap(newUserQuotaRoot -> setQuotaForNewUser(newUserQuotaRoot, quotas)
                    .then(dispatchNewEventQuota(newUserQuotaRoot, newUsername))));
    }

    private Mono<Void> setQuotaForNewUser(QuotaRoot quotaRoot, QuotaManager.Quotas quotas) {
        return setMaxQuota(quotaRoot, quotas)
            .then(setCurrentQuota(quotaRoot, quotas));
    }

    private Mono<Void> setMaxQuota(QuotaRoot quotaRoot, QuotaManager.Quotas quotas) {
        return Mono.zip(setMaxMessagesQuota(quotaRoot, quotas).thenReturn(quotaRoot),
                setMaxStorageQuota(quotaRoot, quotas).thenReturn(quotaRoot))
            .then();
    }

    private Mono<Void> setMaxStorageQuota(QuotaRoot quotaRoot, QuotaManager.Quotas quotas) {
        return Mono.justOrEmpty(Optional.ofNullable(quotas.getStorageQuota().getLimitByScope()
                .get(Quota.Scope.User)))
            .flatMap(quotaSizeLimit -> Mono.from(maxQuotaManager.setMaxStorageReactive(quotaRoot, quotaSizeLimit)));
    }

    private Mono<Void> setMaxMessagesQuota(QuotaRoot quotaRoot, QuotaManager.Quotas quotas) {
        return Mono.justOrEmpty(Optional.ofNullable(quotas.getMessageQuota().getLimitByScope()
                .get(Quota.Scope.User)))
            .flatMap(quotaCountLimit -> Mono.from(maxQuotaManager.setMaxMessageReactive(quotaRoot, quotaCountLimit)));
    }

    private Mono<Void> setCurrentQuota(QuotaRoot quotaRoot, QuotaManager.Quotas quotas) {
        return Mono.from(currentQuotaManager.setCurrentQuotas(QuotaOperation.from(quotaRoot,
            new CurrentQuotas(quotas.getMessageQuota().getUsed(), quotas.getStorageQuota().getUsed()))));
    }

    private Mono<Void> dispatchNewEventQuota(QuotaRoot quotaRoot, Username username) {
        return Mono.from(quotaManager.getQuotasReactive(quotaRoot))
            .flatMap(quotas -> eventBus.dispatch(
                EventFactory.quotaUpdated()
                    .randomEventId()
                    .user(username)
                    .quotaRoot(quotaRoot)
                    .quotaCount(quotas.getMessageQuota())
                    .quotaSize(quotas.getStorageQuota())
                    .instant(Instant.now())
                    .build(),
                NO_REGISTRATION_KEYS));
    }
}
