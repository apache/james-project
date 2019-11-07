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

import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.webadmin.dto.ValidatedQuotaDTO;

public class GlobalQuotaService {

    private final MaxQuotaManager maxQuotaManager;

    @Inject
    public GlobalQuotaService(MaxQuotaManager maxQuotaManager) {
        this.maxQuotaManager = maxQuotaManager;
    }

    public void defineQuota(ValidatedQuotaDTO quota) throws MailboxException {
        Optional<QuotaCountLimit> count = quota.getCount();
        if (count.isPresent()) {
            maxQuotaManager.setGlobalMaxMessage(count.get());
        } else {
            maxQuotaManager.removeGlobalMaxMessage();
        }

        Optional<QuotaSizeLimit> size = quota.getSize();
        if (size.isPresent()) {
            maxQuotaManager.setGlobalMaxStorage(size.get());
        } else {
            maxQuotaManager.removeGlobalMaxStorage();
        }
    }

    public ValidatedQuotaDTO getQuota() throws MailboxException {
        return ValidatedQuotaDTO
            .builder()
            .count(maxQuotaManager.getGlobalMaxMessage())
            .size(maxQuotaManager.getGlobalMaxStorage())
            .build();
    }

    public Optional<QuotaSizeLimit> getMaxSizeQuota() throws MailboxException {
        return maxQuotaManager.getGlobalMaxStorage();
    }

    public void defineMaxSizeQuota(QuotaSizeLimit quotaRequest) throws MailboxException {
        maxQuotaManager.setGlobalMaxStorage(quotaRequest);
    }

    public void deleteMaxSizeQuota() throws MailboxException {
        maxQuotaManager.removeGlobalMaxStorage();
    }

    public Optional<QuotaCountLimit> getMaxCountQuota() throws MailboxException {
        return maxQuotaManager.getGlobalMaxMessage();
    }

    public void defineMaxCountQuota(QuotaCountLimit value) throws MailboxException {
        maxQuotaManager.setGlobalMaxMessage(value);
    }

    public void deleteMaxCountQuota() throws MailboxException {
        maxQuotaManager.removeGlobalMaxMessage();
    }
}
