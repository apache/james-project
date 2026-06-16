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

import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.UserQuotaRootResolver;
import org.apache.james.mailbox.quota.task.RecomputeMailboxCurrentQuotasService;
import org.apache.james.user.api.UsernameChangeTaskStep;
import org.reactivestreams.Publisher;

import reactor.core.publisher.Mono;

public class QuotaUsernameChangeTaskStep implements UsernameChangeTaskStep {
    private final QuotaManager quotaManager;
    private final MaxQuotaManager maxQuotaManager;
    private final RecomputeMailboxCurrentQuotasService recomputeMailboxCurrentQuotasService;
    private final UserQuotaRootResolver userQuotaRootResolver;

    @Inject
    public QuotaUsernameChangeTaskStep(QuotaManager quotaManager,
                                       RecomputeMailboxCurrentQuotasService recomputeMailboxCurrentQuotasService,
                                       UserQuotaRootResolver userQuotaRootResolver,
                                       MaxQuotaManager maxQuotaManager) {
        this.quotaManager = quotaManager;
        this.recomputeMailboxCurrentQuotasService = recomputeMailboxCurrentQuotasService;
        this.userQuotaRootResolver = userQuotaRootResolver;
        this.maxQuotaManager = maxQuotaManager;
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
                .flatMap(newUserQuotaRoot -> setMaxQuota(newUserQuotaRoot, quotas)))
            .then(Mono.from(recomputeMailboxCurrentQuotasService.recomputeCurrentQuotas(newUsername)));
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
}
