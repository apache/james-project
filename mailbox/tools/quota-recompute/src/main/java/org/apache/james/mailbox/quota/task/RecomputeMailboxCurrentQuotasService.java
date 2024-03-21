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

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaComponent;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.model.QuotaOperation;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.CurrentQuotaManager;
import org.apache.james.mailbox.quota.UserQuotaRootResolver;
import org.apache.james.mailbox.store.quota.CurrentQuotaCalculator;

import reactor.core.publisher.Mono;

public class RecomputeMailboxCurrentQuotasService implements RecomputeSingleComponentCurrentQuotasService {

    private final CurrentQuotaManager storeCurrentQuotaManager;
    private final CurrentQuotaCalculator currentQuotaCalculator;
    private final UserQuotaRootResolver userQuotaRootResolver;
    private final SessionProvider sessionProvider;
    private final MailboxManager mailboxManager;

    @Inject
    public RecomputeMailboxCurrentQuotasService(CurrentQuotaManager storeCurrentQuotaManager,
                                         CurrentQuotaCalculator currentQuotaCalculator,
                                         UserQuotaRootResolver userQuotaRootResolver,
                                         SessionProvider sessionProvider,
                                         MailboxManager mailboxManager) {
        this.storeCurrentQuotaManager = storeCurrentQuotaManager;
        this.currentQuotaCalculator = currentQuotaCalculator;
        this.userQuotaRootResolver = userQuotaRootResolver;
        this.sessionProvider = sessionProvider;
        this.mailboxManager = mailboxManager;
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
            .doFinally(any -> mailboxManager.endProcessingRequest(session));
    }
}
