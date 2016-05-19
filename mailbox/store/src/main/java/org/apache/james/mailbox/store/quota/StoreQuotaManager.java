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

package org.apache.james.mailbox.store.quota;

import org.apache.james.mailbox.quota.CurrentQuotaManager;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaRoot;

import javax.inject.Inject;

/**
 * Default implementation for the Quota Manager.
 *
 * Relies on the CurrentQuotaManager and MaxQuotaManager provided.
 */
public class StoreQuotaManager implements QuotaManager {
    private CurrentQuotaManager currentQuotaManager;
    private MaxQuotaManager maxQuotaManager;
    private boolean calculateWhenUnlimited = false;

    public void setCalculateWhenUnlimited(boolean calculateWhenUnlimited) {
        this.calculateWhenUnlimited = calculateWhenUnlimited;
    }

    @Inject
    public void setMaxQuotaManager(MaxQuotaManager maxQuotaManager) {
        this.maxQuotaManager = maxQuotaManager;
    }

    @Inject
    public void setCurrentQuotaManager(CurrentQuotaManager currentQuotaManager) {
        this.currentQuotaManager = currentQuotaManager;
    }

    public Quota getMessageQuota(QuotaRoot quotaRoot) throws MailboxException {
        long maxValue = maxQuotaManager.getMaxMessage(quotaRoot);
        if(maxValue == Quota.UNLIMITED && !calculateWhenUnlimited) {
            return QuotaImpl.quota(Quota.UNKNOWN, Quota.UNLIMITED);
        }
        return QuotaImpl.quota(currentQuotaManager.getCurrentMessageCount(quotaRoot), maxValue);
    }


    public Quota getStorageQuota(QuotaRoot quotaRoot) throws MailboxException {
        long maxValue = maxQuotaManager.getMaxStorage(quotaRoot);
        if(maxValue == Quota.UNLIMITED && !calculateWhenUnlimited) {
            return QuotaImpl.quota(Quota.UNKNOWN, Quota.UNLIMITED);
        }
        return QuotaImpl.quota(currentQuotaManager.getCurrentStorage(quotaRoot), maxValue);
    }

}