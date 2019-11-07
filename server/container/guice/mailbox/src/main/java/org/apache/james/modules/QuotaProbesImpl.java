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

import javax.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.SerializableQuota;
import org.apache.james.mailbox.model.SerializableQuotaLimitValue;
import org.apache.james.mailbox.probe.QuotaProbe;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.utils.GuiceProbe;

import com.github.fge.lambdas.Throwing;

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
    public String getQuotaRoot(String namespace, String user, String name) throws MailboxException {
        return quotaRootResolver.getQuotaRoot(new MailboxPath(namespace, Username.of(user), name)).getValue();
    }

    @Override
    public SerializableQuota<QuotaCountLimit, QuotaCountUsage> getMessageCountQuota(String quotaRoot) throws MailboxException {
        return SerializableQuota.newInstance(quotaManager.getMessageQuota(quotaRootResolver.fromString(quotaRoot)));
    }

    @Override
    public SerializableQuota<QuotaSizeLimit, QuotaSizeUsage> getStorageQuota(String quotaRoot) throws MailboxException {
        return SerializableQuota.newInstance(quotaManager.getStorageQuota(quotaRootResolver.fromString(quotaRoot)));
    }

    @Override
    public SerializableQuotaLimitValue<QuotaCountLimit> getMaxMessageCount(String quotaRoot) throws MailboxException {
        return SerializableQuotaLimitValue.valueOf(maxQuotaManager.getMaxMessage(quotaRootResolver.fromString(quotaRoot)));
    }

    @Override
    public SerializableQuotaLimitValue<QuotaSizeLimit> getMaxStorage(String quotaRoot) throws MailboxException {
        return SerializableQuotaLimitValue.valueOf(maxQuotaManager.getMaxStorage(quotaRootResolver.fromString(quotaRoot)));
    }

    @Override
    public SerializableQuotaLimitValue<QuotaCountLimit> getGlobalMaxMessageCount() throws MailboxException {
        return SerializableQuotaLimitValue.valueOf(maxQuotaManager.getGlobalMaxMessage());
    }

    @Override
    public SerializableQuotaLimitValue<QuotaSizeLimit> getGlobalMaxStorage() throws MailboxException {
        return SerializableQuotaLimitValue.valueOf(maxQuotaManager.getGlobalMaxStorage());
    }

    @Override
    public void setMaxMessageCount(String quotaRoot, SerializableQuotaLimitValue<QuotaCountLimit> maxMessageCount) throws MailboxException {
        maxMessageCount.toValue(QuotaCountLimit::count, QuotaCountLimit.unlimited())
            .ifPresent(
                Throwing.consumer(
                    (QuotaCountLimit value) -> maxQuotaManager.setMaxMessage(quotaRootResolver.fromString(quotaRoot), value))
                    .sneakyThrow());
    }

    @Override
    public void setMaxStorage(String quotaRoot, SerializableQuotaLimitValue<QuotaSizeLimit> maxSize) throws MailboxException {
        maxSize.toValue(QuotaSizeLimit::size, QuotaSizeLimit.unlimited())
            .ifPresent(
                Throwing.consumer(
                    (QuotaSizeLimit value) -> maxQuotaManager.setMaxStorage(quotaRootResolver.fromString(quotaRoot), value))
                    .sneakyThrow());
    }

    @Override
    public void setGlobalMaxMessageCount(SerializableQuotaLimitValue<QuotaCountLimit> maxGlobalMessageCount) throws MailboxException {
        maxGlobalMessageCount.toValue(QuotaCountLimit::count, QuotaCountLimit.unlimited())
            .ifPresent(Throwing.consumer(maxQuotaManager::setGlobalMaxMessage).sneakyThrow());
    }

    @Override
    public void setGlobalMaxStorage(SerializableQuotaLimitValue<QuotaSizeLimit> maxGlobalSize) throws MailboxException {
        maxGlobalSize.toValue(QuotaSizeLimit::size, QuotaSizeLimit.unlimited())
            .ifPresent(Throwing.consumer(maxQuotaManager::setGlobalMaxStorage).sneakyThrow());
    }
}
