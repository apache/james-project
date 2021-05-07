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
package org.apache.james.jmap.draft.utils.quotas;

import java.util.Optional;

import org.apache.james.jmap.draft.model.mailbox.Quotas;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;

import reactor.core.publisher.Mono;

public class QuotaLoaderWithDefaultPreloaded extends QuotaLoader {

    public static Mono<QuotaLoaderWithDefaultPreloaded> preLoad(QuotaRootResolver quotaRootResolver,
                                            QuotaManager quotaManager,
                                            MailboxSession session) {
        DefaultQuotaLoader defaultQuotaLoader = new DefaultQuotaLoader(quotaRootResolver, quotaManager);

        return defaultQuotaLoader.getQuotas(MailboxPath.inbox(session))
            .map(Optional::of)
            .switchIfEmpty(Mono.just(Optional.empty()))
            .map(quotas -> new QuotaLoaderWithDefaultPreloaded(quotaRootResolver, defaultQuotaLoader, quotas));
    }

    private final QuotaRootResolver quotaRootResolver;
    private final DefaultQuotaLoader defaultQuotaLoader;
    private final Optional<Quotas> preloadedUserDefaultQuotas;

    private QuotaLoaderWithDefaultPreloaded(QuotaRootResolver quotaRootResolver, DefaultQuotaLoader defaultQuotaLoader, Optional<Quotas> preloadedUserDefaultQuotas) {
        this.quotaRootResolver = quotaRootResolver;
        this.defaultQuotaLoader = defaultQuotaLoader;
        this.preloadedUserDefaultQuotas = preloadedUserDefaultQuotas;
    }

    public Mono<Quotas> getQuotas(MailboxPath mailboxPath) {
        return Mono.from(quotaRootResolver.getQuotaRootReactive(mailboxPath))
            .flatMap(quotaRoot -> {
                Quotas.QuotaId quotaId = Quotas.QuotaId.fromQuotaRoot(quotaRoot);
                if (containsQuotaId(preloadedUserDefaultQuotas, quotaId)) {
                    return Mono.just(preloadedUserDefaultQuotas.get());
                }
                return defaultQuotaLoader.getQuotas(mailboxPath);
            });
    }

    private boolean containsQuotaId(Optional<Quotas> preloadedUserDefaultQuotas, Quotas.QuotaId quotaId) {
        return preloadedUserDefaultQuotas
            .map(Quotas::getQuotas)
            .map(quotaIdQuotaMap -> quotaIdQuotaMap.containsKey(quotaId))
            .orElse(false);
    }

}
