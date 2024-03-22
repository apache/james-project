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

package org.apache.james.modules;

import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.core.Domain;
import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.probe.QuotaProbe;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.utils.GuiceProbe;

public class QuotaProbesImpl implements QuotaProbe, GuiceProbe {

    private final MaxQuotaManager maxQuotaManager;
    private final QuotaRootResolver quotaRootResolver;
    private final QuotaManager quotaManager;

    @Inject
    public QuotaProbesImpl(MaxQuotaManager maxQuotaManager, QuotaRootResolver quotaRootResolver, QuotaManager quotaManager) {
        this.maxQuotaManager = maxQuotaManager;
        this.quotaRootResolver = quotaRootResolver;
        this.quotaManager = quotaManager;
    }

    @Override
    public QuotaRoot getQuotaRoot(MailboxPath mailboxPath) throws MailboxException {
        return quotaRootResolver.getQuotaRoot(mailboxPath);
    }

    @Override
    public Quota<QuotaCountLimit, QuotaCountUsage> getMessageCountQuota(QuotaRoot quotaRoot) throws MailboxException {
        return quotaManager.getMessageQuota(quotaRoot);
    }

    @Override
    public Quota<QuotaSizeLimit, QuotaSizeUsage> getStorageQuota(QuotaRoot quotaRoot) throws MailboxException {
        return quotaManager.getStorageQuota(quotaRoot);
    }

    @Override
    public Optional<QuotaCountLimit> getMaxMessageCount(QuotaRoot quotaRoot) throws MailboxException {
        return maxQuotaManager.getMaxMessage(quotaRoot);
    }

    @Override
    public Optional<QuotaSizeLimit> getMaxStorage(QuotaRoot quotaRoot) throws MailboxException {
        return maxQuotaManager.getMaxStorage(quotaRoot);
    }

    @Override
    public Optional<QuotaCountLimit> getGlobalMaxMessageCount() throws MailboxException {
        return maxQuotaManager.getGlobalMaxMessage();
    }

    @Override
    public Optional<QuotaSizeLimit> getGlobalMaxStorage() throws MailboxException {
        return maxQuotaManager.getGlobalMaxStorage();
    }

    @Override
    public void setMaxMessageCount(QuotaRoot quotaRoot, QuotaCountLimit maxMessageCount) throws MailboxException {
        maxQuotaManager.setMaxMessage(quotaRoot, maxMessageCount);
    }

    @Override
    public void setMaxStorage(QuotaRoot quotaRoot, QuotaSizeLimit maxSize) throws MailboxException {
        maxQuotaManager.setMaxStorage(quotaRoot, maxSize);
    }

    @Override
    public void setGlobalMaxMessageCount(QuotaCountLimit maxGlobalMessageCount) throws MailboxException {
        maxQuotaManager.setGlobalMaxMessage(maxGlobalMessageCount);
    }

    @Override
    public void setGlobalMaxStorage(QuotaSizeLimit maxGlobalSize) throws MailboxException {
        maxQuotaManager.setGlobalMaxStorage(maxGlobalSize);
    }

    @Override
    public void setDomainMaxMessage(Domain domain, QuotaCountLimit count) throws MailboxException {
        maxQuotaManager.setDomainMaxMessage(domain, count);
    }

    @Override
    public void setDomainMaxStorage(Domain domain, QuotaSizeLimit size) throws MailboxException {
        maxQuotaManager.setDomainMaxStorage(domain, size);
    }
}
