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

import javax.inject.Inject;

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.webadmin.dto.QuotaDTO;
import org.apache.james.webadmin.dto.QuotaRequest;

public class UserQuotaService {

    private final MaxQuotaManager maxQuotaManager;

    @Inject
    public UserQuotaService(MaxQuotaManager maxQuotaManager) {
        this.maxQuotaManager = maxQuotaManager;
    }

    public void defineQuota(String user, QuotaDTO quota) throws MailboxException {
        QuotaRoot quotaRoot = QuotaRoot.forUser(user);
        maxQuotaManager.setMaxMessage(quotaRoot, quota.getCount());
        maxQuotaManager.setMaxStorage(quotaRoot, quota.getSize());
    }

    public QuotaDTO getQuota(String user) throws MailboxException {
        QuotaRoot quotaRoot = QuotaRoot.forUser(user);
        return QuotaDTO
            .builder()
            .count(maxQuotaManager.getMaxMessage(quotaRoot))
            .size(maxQuotaManager.getMaxStorage(quotaRoot))
            .build();
    }

    public Long getMaxSizeQuota(String user) throws MailboxException {
        return maxQuotaManager.getMaxStorage(QuotaRoot.forUser(user));
    }

    public void defineMaxSizeQuota(String user, QuotaRequest quotaRequest) throws MailboxException {
        maxQuotaManager.setMaxStorage(QuotaRoot.forUser(user), quotaRequest.getValue());
    }

    public void deleteMaxSizeQuota(String user) throws MailboxException {
        maxQuotaManager.setMaxStorage(QuotaRoot.forUser(user), Quota.UNLIMITED);
    }

    public Long getMaxCountQuota(String user) throws MailboxException {
        return maxQuotaManager.getMaxMessage(QuotaRoot.forUser(user));
    }

    public void defineMaxCountQuota(String user, QuotaRequest quotaRequest) throws MailboxException {
        maxQuotaManager.setMaxMessage(QuotaRoot.forUser(user), quotaRequest.getValue());
    }

    public void deleteMaxCountQuota(String user) throws MailboxException {
        maxQuotaManager.setMaxMessage(QuotaRoot.forUser(user), Quota.UNLIMITED);
    }
}
