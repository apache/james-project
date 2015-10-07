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

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;

public class QuotaManagement implements QuotaManagementMBean {

    private QuotaManager quotaManager;
    private MaxQuotaManager maxQuotaManager;
    private QuotaRootResolver quotaRootResolver;

    public void setQuotaManager(QuotaManager quotaManager) {
        this.quotaManager = quotaManager;
    }

    public void setMaxQuotaManager(MaxQuotaManager maxQuotaManager) {
        this.maxQuotaManager = maxQuotaManager;
    }

    public void setQuotaRootResolver(QuotaRootResolver quotaRootResolver) {
        this.quotaRootResolver = quotaRootResolver;
    }

    @Override
    public String getQuotaRoot(String namespace, String user, String name) throws MailboxException {
        return quotaRootResolver.getQuotaRoot(new MailboxPath(namespace, user, name)).getValue();
    }

    @Override
    public long getMaxMessageCount(String quotaRoot) throws MailboxException {
        return maxQuotaManager.getMaxMessage(quotaRootResolver.createQuotaRoot(quotaRoot));
    }

    @Override
    public long getMaxStorage(String quotaRoot) throws MailboxException {
        return maxQuotaManager.getMaxStorage(quotaRootResolver.createQuotaRoot(quotaRoot));
    }

    @Override
    public long getDefaultMaxMessageCount() throws MailboxException {
        return maxQuotaManager.getDefaultMaxMessage();
    }

    @Override
    public long getDefaultMaxStorage() throws MailboxException {
        return maxQuotaManager.getDefaultMaxStorage();
    }

    @Override
    public void setMaxMessageCount(String quotaRoot, long maxMessageCount) throws MailboxException {
        maxQuotaManager.setMaxMessage(quotaRootResolver.createQuotaRoot(quotaRoot), maxMessageCount);
    }

    @Override
    public void setMaxStorage(String quotaRoot, long maxSize) throws MailboxException {
        maxQuotaManager.setMaxStorage(quotaRootResolver.createQuotaRoot(quotaRoot), maxSize);
    }

    @Override
    public void setDefaultMaxMessageCount(long maxDefaultMessageCount) throws MailboxException {
        maxQuotaManager.setDefaultMaxMessage(maxDefaultMessageCount);
    }

    @Override
    public void setDefaultMaxStorage(long maxDefaultSize) throws MailboxException {
        maxQuotaManager.setDefaultMaxStorage(maxDefaultSize);
    }

    @Override
    public SerializableQuota getMessageCountQuota(String quotaRoot) throws MailboxException {
        return new SerializableQuota(quotaManager.getMessageQuota(quotaRootResolver.createQuotaRoot(quotaRoot)));
    }

    @Override
    public SerializableQuota getStorageQuota(String quotaRoot) throws MailboxException {
        return new SerializableQuota(quotaManager.getStorageQuota(quotaRootResolver.createQuotaRoot(quotaRoot)));
    }
}
