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
package org.apache.james.webadmin.service;

import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.mailbox.quota.QuotaCount;
import org.apache.james.mailbox.quota.QuotaSize;
import org.apache.james.webadmin.dto.QuotaDTO;

public class GlobalQuotaService {

    private final MaxQuotaManager maxQuotaManager;

    @Inject
    public GlobalQuotaService(MaxQuotaManager maxQuotaManager) {
        this.maxQuotaManager = maxQuotaManager;
    }

    public void defineQuota(QuotaDTO quota) throws MailboxException {
        Optional<QuotaCount> count = quota.getCount();
        if (count.isPresent()) {
            maxQuotaManager.setDefaultMaxMessage(count.get());
        } else {
            maxQuotaManager.removeDefaultMaxMessage();
        }

        Optional<QuotaSize> size = quota.getSize();
        if (size.isPresent()) {
            maxQuotaManager.setDefaultMaxStorage(size.get());
        } else {
            maxQuotaManager.removeDefaultMaxStorage();
        }
    }

    public QuotaDTO getQuota() throws MailboxException {
        return QuotaDTO
            .builder()
            .count(maxQuotaManager.getDefaultMaxMessage())
            .size(maxQuotaManager.getDefaultMaxStorage())
            .build();
    }

    public Optional<QuotaSize> getMaxSizeQuota() throws MailboxException {
        return maxQuotaManager.getDefaultMaxStorage();
    }

    public void defineMaxSizeQuota(QuotaSize quotaRequest) throws MailboxException {
        maxQuotaManager.setDefaultMaxStorage(quotaRequest);
    }

    public void deleteMaxSizeQuota() throws MailboxException {
        maxQuotaManager.removeDefaultMaxStorage();
    }

    public Optional<QuotaCount> getMaxCountQuota() throws MailboxException {
        return maxQuotaManager.getDefaultMaxMessage();
    }

    public void defineMaxCountQuota(QuotaCount value) throws MailboxException {
        maxQuotaManager.setDefaultMaxMessage(value);
    }

    public void deleteMaxCountQuota() throws MailboxException {
        maxQuotaManager.removeDefaultMaxMessage();
    }
}
