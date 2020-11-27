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
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;

public class QuotaLoaderWithDefaultPreloaded extends QuotaLoader {

    private final QuotaRootResolver quotaRootResolver;
    private final QuotaManager quotaManager;
    private final Optional<Quotas> preloadedUserDefaultQuotas;
    private final MailboxSession session;

    public QuotaLoaderWithDefaultPreloaded(QuotaRootResolver quotaRootResolver,
                                           QuotaManager quotaManager,
                                           MailboxSession session) throws MailboxException {
        this.quotaRootResolver = quotaRootResolver;
        this.quotaManager = quotaManager;
        this.session = session;
        preloadedUserDefaultQuotas = Optional.of(getUserDefaultQuotas());

    }

    public Quotas getQuotas(MailboxPath mailboxPath) throws MailboxException {
        QuotaRoot quotaRoot = quotaRootResolver.getQuotaRoot(mailboxPath);
        Quotas.QuotaId quotaId = Quotas.QuotaId.fromQuotaRoot(quotaRoot);

        if (containsQuotaId(preloadedUserDefaultQuotas, quotaId)) {
            return preloadedUserDefaultQuotas.get();
        }
        QuotaManager.Quotas quotas = quotaManager.getQuotas(quotaRoot);
        return Quotas.from(
            quotaId,
            Quotas.Quota.from(
                quotaToValue(quotas.getStorageQuota()),
                quotaToValue(quotas.getMessageQuota())));
    }

    private boolean containsQuotaId(Optional<Quotas> preloadedUserDefaultQuotas, Quotas.QuotaId quotaId) {
        return preloadedUserDefaultQuotas
            .map(Quotas::getQuotas)
            .map(quotaIdQuotaMap -> quotaIdQuotaMap.containsKey(quotaId))
            .orElse(false);
    }

    private Quotas getUserDefaultQuotas() throws MailboxException {
        QuotaRoot quotaRoot = quotaRootResolver.getQuotaRoot(MailboxPath.inbox(session));
        Quotas.QuotaId quotaId = Quotas.QuotaId.fromQuotaRoot(quotaRoot);
        QuotaManager.Quotas quotas = quotaManager.getQuotas(quotaRoot);
        return Quotas.from(
            quotaId,
            Quotas.Quota.from(
                quotaToValue(quotas.getStorageQuota()),
                quotaToValue(quotas.getMessageQuota())));
    }

}
