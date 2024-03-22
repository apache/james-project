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

import jakarta.inject.Inject;

import org.apache.james.jmap.draft.model.mailbox.Quotas;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;

import reactor.core.publisher.Mono;

public class DefaultQuotaLoader extends QuotaLoader {

    private final QuotaRootResolver quotaRootResolver;
    private final QuotaManager quotaManager;

    @Inject
    public DefaultQuotaLoader(QuotaRootResolver quotaRootResolver, QuotaManager quotaManager) {
        this.quotaRootResolver = quotaRootResolver;
        this.quotaManager = quotaManager;
    }

    public Mono<Quotas> getQuotas(MailboxPath mailboxPath) {
        return Mono.from(quotaRootResolver.getQuotaRootReactive(mailboxPath))
            .flatMap(quotaRoot -> Mono.from(quotaManager.getQuotasReactive(quotaRoot))
                .map(quotas -> {
                    Quotas.QuotaId quotaId = Quotas.QuotaId.fromQuotaRoot(quotaRoot);

                    return Quotas.from(
                        quotaId,
                        Quotas.Quota.from(
                            quotaToValue(quotas.getStorageQuota()),
                            quotaToValue(quotas.getMessageQuota())));
                }));
    }

}
