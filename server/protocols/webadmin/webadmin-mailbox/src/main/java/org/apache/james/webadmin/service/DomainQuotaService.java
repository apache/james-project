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
import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.webadmin.dto.QuotaDomainDTO;
import org.apache.james.webadmin.dto.ValidatedQuotaDTO;

public class DomainQuotaService {

    private final MaxQuotaManager maxQuotaManager;

    @Inject
    public DomainQuotaService(MaxQuotaManager maxQuotaManager) {
        this.maxQuotaManager = maxQuotaManager;
    }

    public Optional<QuotaCountLimit> getMaxCountQuota(Domain domain) {
        return maxQuotaManager.getDomainMaxMessage(domain);
    }

    public void setMaxCountQuota(Domain domain, QuotaCountLimit quotaCount) throws MailboxException {
        maxQuotaManager.setDomainMaxMessage(domain, quotaCount);
    }

    public void remoteMaxQuotaCount(Domain domain) throws MailboxException {
        maxQuotaManager.removeDomainMaxMessage(domain);
    }

    public Optional<QuotaSizeLimit> getMaxSizeQuota(Domain domain) {
        return maxQuotaManager.getDomainMaxStorage(domain);
    }

    public void setMaxSizeQuota(Domain domain, QuotaSizeLimit quotaSize) throws MailboxException {
        maxQuotaManager.setDomainMaxStorage(domain, quotaSize);
    }

    public void remoteMaxQuotaSize(Domain domain) throws MailboxException {
        maxQuotaManager.removeDomainMaxStorage(domain);
    }

    public QuotaDomainDTO getQuota(Domain domain) throws MailboxException {
        return QuotaDomainDTO.builder()
            .domain(ValidatedQuotaDTO
                .builder()
                .count(maxQuotaManager.getDomainMaxMessage(domain))
                .size(maxQuotaManager.getDomainMaxStorage(domain)))
            .global(ValidatedQuotaDTO
                .builder()
                .count(maxQuotaManager.getGlobalMaxMessage())
                .size(maxQuotaManager.getGlobalMaxStorage()))
            .computed(ValidatedQuotaDTO
                .builder()
                .count(maxQuotaManager.getComputedMaxMessage(domain))
                .size(maxQuotaManager.getComputedMaxStorage(domain)))
            .build();
    }

    public void defineQuota(Domain domain, ValidatedQuotaDTO quota) {
        try {
            if (quota.getCount().isPresent()) {
                maxQuotaManager.setDomainMaxMessage(domain, quota.getCount().get());
            } else {
                maxQuotaManager.removeDomainMaxMessage(domain);
            }

            if (quota.getSize().isPresent()) {
                maxQuotaManager.setDomainMaxStorage(domain, quota.getSize().get());
            } else {
                maxQuotaManager.removeDomainMaxStorage(domain);
            }
        } catch (MailboxException e) {
            throw new RuntimeException(e);
        }
    }
}
