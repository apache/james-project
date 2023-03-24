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
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.webadmin.dto.ValidatedQuotaDTO;

import reactor.core.publisher.Mono;

public class GlobalQuotaService {

    private final MaxQuotaManager maxQuotaManager;

    @Inject
    public GlobalQuotaService(MaxQuotaManager maxQuotaManager) {
        this.maxQuotaManager = maxQuotaManager;
    }

    public void defineQuota(ValidatedQuotaDTO quota) {
        defineGlobalMaxMessage(quota)
            .then(defineGlobalMaxStorage(quota))
            .block();
    }

    private Mono<Void> defineGlobalMaxStorage(ValidatedQuotaDTO quota) {
        return quota.getSize()
            .map(sizeLimit -> Mono.from(maxQuotaManager.setGlobalMaxStorageReactive(sizeLimit)))
            .orElseGet(() -> Mono.from(maxQuotaManager.removeGlobalMaxStorageReactive()));
    }

    private Mono<Void> defineGlobalMaxMessage(ValidatedQuotaDTO quota) {
        return quota.getCount()
            .map(countLimit -> Mono.from(maxQuotaManager.setGlobalMaxMessageReactive(countLimit)))
            .orElseGet(() -> Mono.from(maxQuotaManager.removeGlobalMaxMessageReactive()));
    }

    public ValidatedQuotaDTO getQuota() {
        return Mono.zip(Mono.from(maxQuotaManager.getGlobalMaxMessageReactive())
                    .map(Optional::of)
                    .switchIfEmpty(Mono.just(Optional.empty())),
                Mono.from(maxQuotaManager.getGlobalMaxStorageReactive())
                    .map(Optional::of)
                    .switchIfEmpty(Mono.just(Optional.empty())))
            .map(tuple -> ValidatedQuotaDTO
                .builder()
                .count(tuple.getT1())
                .size(tuple.getT2())
                .build())
            .block();
    }

    public Optional<QuotaSizeLimit> getMaxSizeQuota() {
        return Mono.from(maxQuotaManager.getGlobalMaxStorageReactive()).blockOptional();
    }

    public void defineMaxSizeQuota(QuotaSizeLimit quotaRequest) {
        Mono.from(maxQuotaManager.setGlobalMaxStorageReactive(quotaRequest)).block();
    }

    public void deleteMaxSizeQuota() {
        Mono.from(maxQuotaManager.removeGlobalMaxStorageReactive()).block();
    }

    public Optional<QuotaCountLimit> getMaxCountQuota() {
        return Mono.from(maxQuotaManager.getGlobalMaxMessageReactive()).blockOptional();
    }

    public void defineMaxCountQuota(QuotaCountLimit value) {
        Mono.from(maxQuotaManager.setGlobalMaxMessageReactive(value)).block();
    }

    public void deleteMaxCountQuota() {
        Mono.from(maxQuotaManager.removeGlobalMaxMessageReactive()).block();
    }
}
