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

public class JmxQuotaProbe implements JmxProbe {
    private static final String QUOTAMANAGER_OBJECT_NAME = "org.apache.james:type=component,name=quotamanagerbean";

    private QuotaManagementMBean quotaManagement;

    public JmxQuotaProbe connect(JmxConnection jmxc) throws IOException {
        try {
            quotaManagement = jmxc.retrieveBean(QuotaManagementMBean.class, QUOTAMANAGER_OBJECT_NAME);
        } catch (MalformedObjectNameException e) {
            throw new RuntimeException("Invalid ObjectName? Please report this as a bug.", e);
        }
        return this;
    }

    public String getQuotaRoot(String namespace, String user, String name) throws MailboxException {
        return quotaManagement.getQuotaRoot(namespace, user, name);
    }

    public SerializableQuota<QuotaCountLimit, QuotaCountUsage> getMessageCountQuota(String quotaRoot) throws MailboxException {
        return quotaManagement.getMessageCountQuota(quotaRoot);
    }

    public SerializableQuota<QuotaSizeLimit, QuotaSizeUsage> getStorageQuota(String quotaRoot) throws MailboxException {
        return quotaManagement.getStorageQuota(quotaRoot);
    }

    public SerializableQuotaLimitValue<QuotaCountLimit> getMaxMessageCount(String quotaRoot) throws MailboxException {
        return quotaManagement.getMaxMessageCount(quotaRoot);
    }

    public SerializableQuotaLimitValue<QuotaSizeLimit> getMaxStorage(String quotaRoot) throws MailboxException {
        return quotaManagement.getMaxStorage(quotaRoot);
    }

    public SerializableQuotaLimitValue<QuotaCountLimit> getGlobalMaxMessageCount() throws MailboxException {
        return quotaManagement.getGlobalMaxMessageCount();
    }

    public SerializableQuotaLimitValue<QuotaSizeLimit> getGlobalMaxStorage() throws MailboxException {
        return quotaManagement.getGlobalMaxStorage();
    }

    public void setMaxMessageCount(String quotaRoot, SerializableQuotaLimitValue<QuotaCountLimit> maxMessageCount) throws MailboxException {
        quotaManagement.setMaxMessageCount(quotaRoot, maxMessageCount);
    }

    public void setMaxStorage(String quotaRoot, SerializableQuotaLimitValue<QuotaSizeLimit> maxSize) throws MailboxException {
        quotaManagement.setMaxStorage(quotaRoot, maxSize);
    }

    public void setGlobalMaxMessageCount(SerializableQuotaLimitValue<QuotaCountLimit> maxGlobalMessageCount) throws MailboxException {
        quotaManagement.setGlobalMaxMessageCount(maxGlobalMessageCount);
    }

    public void setGlobalMaxStorage(SerializableQuotaLimitValue<QuotaSizeLimit> maxGlobalSize) throws MailboxException {
        quotaManagement.setGlobalMaxStorage(maxGlobalSize);
    }
}