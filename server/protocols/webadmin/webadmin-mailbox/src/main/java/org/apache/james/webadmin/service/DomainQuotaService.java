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

import org.apache.james.core.Domain;
import org.apache.james.core.quota.QuotaCount;
import org.apache.james.core.quota.QuotaSize;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.webadmin.dto.QuotaDTO;

import com.github.fge.lambdas.Throwing;

public class DomainQuotaService {

    private final MaxQuotaManager maxQuotaManager;

    @Inject
    public DomainQuotaService(MaxQuotaManager maxQuotaManager) {
        this.maxQuotaManager = maxQuotaManager;
    }

    public Optional<QuotaCount> getMaxCountQuota(Domain domain) {
        return maxQuotaManager.getDomainMaxMessage(domain);
    }

    public void setMaxCountQuota(Domain domain, QuotaCount quotaCount) throws MailboxException {
        maxQuotaManager.setDomainMaxMessage(domain, quotaCount);
    }

    public void remoteMaxQuotaCount(Domain domain) throws MailboxException {
        maxQuotaManager.removeDomainMaxMessage(domain);
    }

    public Optional<QuotaSize> getMaxSizeQuota(Domain domain) {
        return maxQuotaManager.getDomainMaxStorage(domain);
    }

    public void setMaxSizeQuota(Domain domain, QuotaSize quotaSize) throws MailboxException {
        maxQuotaManager.setDomainMaxStorage(domain, quotaSize);
    }

    public void remoteMaxQuotaSize(Domain domain) throws MailboxException {
        maxQuotaManager.removeDomainMaxStorage(domain);
    }

    public QuotaDTO getQuota(Domain domain) {
        return QuotaDTO
            .builder()
            .count(maxQuotaManager.getDomainMaxMessage(domain))
            .size(maxQuotaManager.getDomainMaxStorage(domain))
            .build();
    }

    public void defineQuota(Domain domain, QuotaDTO quota) {
        quota.getCount()
            .ifPresent(Throwing.consumer(count -> maxQuotaManager.setDomainMaxMessage(domain, count)));
        quota.getSize()
            .ifPresent(Throwing.consumer(size -> maxQuotaManager.setDomainMaxStorage(domain, size)));
    }
}
