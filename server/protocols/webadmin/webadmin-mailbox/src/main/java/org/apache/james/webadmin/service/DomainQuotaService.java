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
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.webadmin.dto.QuotaDomainDTO;
import org.apache.james.webadmin.dto.ValidatedQuotaDTO;

import reactor.core.publisher.Mono;

public class DomainQuotaService {

    private final MaxQuotaManager maxQuotaManager;

    @Inject
    public DomainQuotaService(MaxQuotaManager maxQuotaManager) {
        this.maxQuotaManager = maxQuotaManager;
    }

    public Optional<QuotaCountLimit> getMaxCountQuota(Domain domain) {
        return Mono.from(maxQuotaManager.getDomainMaxMessageReactive(domain)).blockOptional();
    }

    public void setMaxCountQuota(Domain domain, QuotaCountLimit quotaCount) {
        Mono.from(maxQuotaManager.setDomainMaxMessageReactive(domain, quotaCount)).block();
    }

    public void remoteMaxQuotaCount(Domain domain) {
        Mono.from(maxQuotaManager.removeDomainMaxMessageReactive(domain)).block();
    }

    public Optional<QuotaSizeLimit> getMaxSizeQuota(Domain domain) {
        return Mono.from(maxQuotaManager.getDomainMaxStorageReactive(domain)).blockOptional();
    }

    public void setMaxSizeQuota(Domain domain, QuotaSizeLimit quotaSize) {
        Mono.from(maxQuotaManager.setDomainMaxStorageReactive(domain, quotaSize)).block();
    }

    public void remoteMaxQuotaSize(Domain domain) {
        Mono.from(maxQuotaManager.removeDomainMaxStorageReactive(domain)).block();
    }

    public QuotaDomainDTO getQuota(Domain domain) {
        Mono<Optional<QuotaCountLimit>> getDomainMaxMessage = Mono.from(maxQuotaManager.getDomainMaxMessageReactive(domain))
            .map(Optional::of)
            .defaultIfEmpty(Optional.empty());
        Mono<Optional<QuotaSizeLimit>> getDomainMaxStorage = Mono.from(maxQuotaManager.getDomainMaxStorageReactive(domain))
            .map(Optional::of)
            .defaultIfEmpty(Optional.empty());

        Mono<Optional<QuotaCountLimit>> getGlobalMaxMessage = Mono.from(maxQuotaManager.getGlobalMaxMessageReactive())
            .map(Optional::of)
            .defaultIfEmpty(Optional.empty());
        Mono<Optional<QuotaSizeLimit>> getGlobalMaxStorage = Mono.from(maxQuotaManager.getGlobalMaxStorageReactive())
            .map(Optional::of)
            .defaultIfEmpty(Optional.empty());

        return Mono.zip(getDomainMaxMessage,
                getDomainMaxStorage,
                getGlobalMaxMessage,
                getGlobalMaxStorage)
            .map(tuple4 -> QuotaDomainDTO.builder()
                .domain(ValidatedQuotaDTO
                    .builder()
                    .count(tuple4.getT1())
                    .size(tuple4.getT2()))
                .global(ValidatedQuotaDTO
                    .builder()
                    .count(tuple4.getT3())
                    .size(tuple4.getT4()))
                .computed(ValidatedQuotaDTO
                    .builder()
                    .count(tuple4.getT1().or(tuple4::getT3))
                    .size(tuple4.getT2().or(tuple4::getT4)))
                .build())
            .block();
    }

    public void defineQuota(Domain domain, ValidatedQuotaDTO quota) {
        defineDomainMaxMessage(domain, quota)
            .then(defineDomainMaxStorage(domain, quota))
            .block();
    }

    private Mono<Void> defineDomainMaxStorage(Domain domain, ValidatedQuotaDTO quota) {
        return quota.getSize()
            .map(sizeLimit -> Mono.from(maxQuotaManager.setDomainMaxStorageReactive(domain, sizeLimit)))
            .orElseGet(() -> Mono.from(maxQuotaManager.removeDomainMaxStorageReactive(domain)));
    }

    private Mono<Void> defineDomainMaxMessage(Domain domain, ValidatedQuotaDTO quota) {
        return quota.getCount()
            .map(countLimit -> Mono.from(maxQuotaManager.setDomainMaxMessageReactive(domain, countLimit)))
            .orElseGet(() -> Mono.from(maxQuotaManager.removeDomainMaxMessageReactive(domain)));
    }
}
