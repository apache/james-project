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

package org.apache.james.cli.probe.impl;

import java.io.IOException;

import javax.management.MalformedObjectNameException;

import org.apache.james.adapter.mailbox.QuotaManagementMBean;
import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.SerializableQuota;
import org.apache.james.mailbox.model.SerializableQuotaLimitValue;
import org.apache.james.mailbox.probe.QuotaProbe;

public class JmxQuotaProbe implements QuotaProbe, JmxProbe {
    private static final String QUOTAMANAGER_OBJECT_NAME = "org.apache.james:type=component,name=quotamanagerbean";

    private QuotaManagementMBean quotaManagement;

    @Override
    public JmxQuotaProbe connect(JmxConnection jmxc) throws IOException {
        try {
            quotaManagement = jmxc.retrieveBean(QuotaManagementMBean.class, QUOTAMANAGER_OBJECT_NAME);
        } catch (MalformedObjectNameException e) {
            throw new RuntimeException("Invalid ObjectName? Please report this as a bug.", e);
        }
        return this;
    }

    @Override
    public String getQuotaRoot(String namespace, String user, String name) throws MailboxException {
        return quotaManagement.getQuotaRoot(namespace, user, name);
    }

    @Override
    public SerializableQuota<QuotaCountLimit, QuotaCountUsage> getMessageCountQuota(String quotaRoot) throws MailboxException {
        return quotaManagement.getMessageCountQuota(quotaRoot);
    }

    @Override
    public SerializableQuota<QuotaSizeLimit, QuotaSizeUsage> getStorageQuota(String quotaRoot) throws MailboxException {
        return quotaManagement.getStorageQuota(quotaRoot);
    }

    @Override
    public SerializableQuotaLimitValue<QuotaCountLimit> getMaxMessageCount(String quotaRoot) throws MailboxException {
        return quotaManagement.getMaxMessageCount(quotaRoot);
    }

    @Override
    public SerializableQuotaLimitValue<QuotaSizeLimit> getMaxStorage(String quotaRoot) throws MailboxException {
        return quotaManagement.getMaxStorage(quotaRoot);
    }

    @Override
    public SerializableQuotaLimitValue<QuotaCountLimit> getGlobalMaxMessageCount() throws MailboxException {
        return quotaManagement.getGlobalMaxMessageCount();
    }

    @Override
    public SerializableQuotaLimitValue<QuotaSizeLimit> getGlobalMaxStorage() throws MailboxException {
        return quotaManagement.getGlobalMaxStorage();
    }

    @Override
    public void setMaxMessageCount(String quotaRoot, SerializableQuotaLimitValue<QuotaCountLimit> maxMessageCount) throws MailboxException {
        quotaManagement.setMaxMessageCount(quotaRoot, maxMessageCount);
    }

    @Override
    public void setMaxStorage(String quotaRoot, SerializableQuotaLimitValue<QuotaSizeLimit> maxSize) throws MailboxException {
        quotaManagement.setMaxStorage(quotaRoot, maxSize);
    }

    @Override
    public void setGlobalMaxMessageCount(SerializableQuotaLimitValue<QuotaCountLimit> maxGlobalMessageCount) throws MailboxException {
        quotaManagement.setGlobalMaxMessageCount(maxGlobalMessageCount);
    }

    @Override
    public void setGlobalMaxStorage(SerializableQuotaLimitValue<QuotaSizeLimit> maxGlobalSize) throws MailboxException {
        quotaManagement.setGlobalMaxStorage(maxGlobalSize);
    }

}