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

import javax.inject.Inject;

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.CurrentQuotaManager;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.mailbox.quota.QuotaCount;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaSize;

/**
 * Default implementation for the Quota Manager.
 *
 * Relies on the CurrentQuotaManager and MaxQuotaManager provided.
 */
public class StoreQuotaManager implements QuotaManager {
    private final CurrentQuotaManager currentQuotaManager;
    private final MaxQuotaManager maxQuotaManager;

    @Inject
    public StoreQuotaManager(CurrentQuotaManager currentQuotaManager, MaxQuotaManager maxQuotaManager) {
        this.currentQuotaManager = currentQuotaManager;
        this.maxQuotaManager = maxQuotaManager;
    }

    public Quota<QuotaCount> getMessageQuota(QuotaRoot quotaRoot) throws MailboxException {
        return Quota.quota(
            currentQuotaManager.getCurrentMessageCount(quotaRoot),
            maxQuotaManager.getMaxMessage(quotaRoot).orElse(QuotaCount.unlimited()));
    }


    public Quota<QuotaSize> getStorageQuota(QuotaRoot quotaRoot) throws MailboxException {
        return Quota.quota(
            currentQuotaManager.getCurrentStorage(quotaRoot),
            maxQuotaManager.getMaxStorage(quotaRoot).orElse(QuotaSize.unlimited()));
    }

}