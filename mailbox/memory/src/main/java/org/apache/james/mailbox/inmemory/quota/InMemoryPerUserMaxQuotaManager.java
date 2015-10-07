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
package org.apache.james.mailbox.inmemory.quota;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaRoot;

import javax.inject.Singleton;

@Singleton
public class InMemoryPerUserMaxQuotaManager implements MaxQuotaManager {

    private long maxMessage = Quota.UNLIMITED;
    private long maxStorage = Quota.UNLIMITED;

    private Map<String, Long> userMaxStorage = new ConcurrentHashMap<String, Long>();
    private Map<String, Long> userMaxMessage = new ConcurrentHashMap<String, Long>();

    @Override
    public void setDefaultMaxStorage(long maxStorage) throws MailboxException {
        this.maxStorage = maxStorage;
    }

    @Override
    public void setDefaultMaxMessage(long maxMessage) throws MailboxException {
        this.maxMessage = maxMessage;
    }

    @Override
    public long getMaxStorage(QuotaRoot quotaRoot) throws MailboxException {
        Long max = userMaxStorage.get(quotaRoot.getValue());
        if (max == null) {
            return maxStorage;
        }
        return max;
    }

    @Override
    public long getMaxMessage(QuotaRoot quotaRoot) throws MailboxException {
        Long max = userMaxMessage.get(quotaRoot.getValue());
        if (max == null) {
            return maxMessage;
        }
        return max;
    }

    @Override
    public void setMaxStorage(QuotaRoot user, long maxStorageQuota) {
        userMaxStorage.put(user.getValue(), maxStorageQuota);
    }

    @Override
    public void setMaxMessage(QuotaRoot quotaRoot, long maxMessageCount) {
        userMaxMessage.put(quotaRoot.getValue(), maxMessageCount);
    }

    @Override
    public long getDefaultMaxStorage() throws MailboxException {
        return maxStorage;
    }

    @Override
    public long getDefaultMaxMessage() throws MailboxException {
        return maxMessage;
    }
}
