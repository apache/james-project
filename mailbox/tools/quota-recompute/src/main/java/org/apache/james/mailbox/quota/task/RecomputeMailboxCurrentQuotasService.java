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

package org.apache.james.mailbox.quota.task;

import java.time.Instant;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaComponent;
import org.apache.james.events.EventBus;
import org.apache.james.events.RegistrationKey;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.model.QuotaOperation;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.CurrentQuotaManager;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.UserQuotaRootResolver;
import org.apache.james.mailbox.store.event.EventFactory;
import org.apache.james.mailbox.store.quota.CurrentQuotaCalculator;

import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Mono;

public class RecomputeMailboxCurrentQuotasService implements RecomputeSingleComponentCurrentQuotasService {

    private static final ImmutableSet<RegistrationKey> NO_REGISTRATION_KEYS = ImmutableSet.of();

    private final CurrentQuotaManager storeCurrentQuotaManager;
    private final CurrentQuotaCalculator currentQuotaCalculator;
    private final UserQuotaRootResolver userQuotaRootResolver;
    private final SessionProvider sessionProvider;
    private final MailboxManager mailboxManager;
    private final QuotaManager quotaManager;
    private final EventBus eventBus;

    @Inject
    public RecomputeMailboxCurrentQuotasService(CurrentQuotaManager storeCurrentQuotaManager,
                                         CurrentQuotaCalculator currentQuotaCalculator,
                                         UserQuotaRootResolver userQuotaRootResolver,
                                         SessionProvider sessionProvider,
                                         MailboxManager mailboxManager,
                                         QuotaManager quotaManager,
                                         EventBus eventBus) {
        this.storeCurrentQuotaManager = storeCurrentQuotaManager;
        this.currentQuotaCalculator = currentQuotaCalculator;
        this.userQuotaRootResolver = userQuotaRootResolver;
        this.sessionProvider = sessionProvider;
        this.mailboxManager = mailboxManager;
        this.quotaManager = quotaManager;
        this.eventBus = eventBus;
    }


    @Override
    public QuotaComponent getQuotaComponent() {
        return QuotaComponent.MAILBOX;
    }

    @Override
    public Mono<Void> recomputeCurrentQuotas(Username username) {
        MailboxSession session = sessionProvider.createSystemSession(username);
        QuotaRoot quotaRoot = userQuotaRootResolver.forUser(username);

        return currentQuotaCalculator.recalculateCurrentQuotas(quotaRoot, session)
            .map(recalculatedQuotas -> QuotaOperation.from(quotaRoot, recalculatedQuotas))
            .flatMap(quotaOperation -> Mono.from(storeCurrentQuotaManager.setCurrentQuotas(quotaOperation)))
            .then(dispatchQuotaUpdateEvent(username, quotaRoot))
            .doFinally(any -> mailboxManager.endProcessingRequest(session));
    }

    private Mono<Void> dispatchQuotaUpdateEvent(Username username, QuotaRoot quotaRoot) {
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
