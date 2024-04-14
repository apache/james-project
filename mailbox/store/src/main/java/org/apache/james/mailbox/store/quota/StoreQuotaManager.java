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

import java.util.Map;

import jakarta.inject.Inject;

import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.mailbox.model.CurrentQuotas;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.Quota.Scope;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.CurrentQuotaManager;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.mailbox.quota.QuotaManager;
import org.reactivestreams.Publisher;

import reactor.core.publisher.Mono;

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

    @Override
    public Quota<QuotaCountLimit, QuotaCountUsage> getMessageQuota(QuotaRoot quotaRoot) {
        Map<Scope, QuotaCountLimit> maxMessageDetails = maxQuotaManager.listMaxMessagesDetails(quotaRoot);
        return Quota.<QuotaCountLimit, QuotaCountUsage>builder()
            .used(Mono.from(currentQuotaManager.getCurrentMessageCount(quotaRoot)).block().sanitize())
            .computedLimit(maxQuotaManager.getMaxMessage(maxMessageDetails).orElse(QuotaCountLimit.unlimited()))
            .limitsByScope(maxMessageDetails)
            .build();
    }


    @Override
    public Quota<QuotaSizeLimit, QuotaSizeUsage> getStorageQuota(QuotaRoot quotaRoot) {
        Map<Scope, QuotaSizeLimit> maxStorageDetails = maxQuotaManager.listMaxStorageDetails(quotaRoot);
        return Quota.<QuotaSizeLimit, QuotaSizeUsage>builder()
            .used(Mono.from(currentQuotaManager.getCurrentStorage(quotaRoot)).block().sanitize())
            .computedLimit(maxQuotaManager.getMaxStorage(maxStorageDetails).orElse(QuotaSizeLimit.unlimited()))
            .limitsByScope(maxStorageDetails)
            .build();
    }

    @Override
    public Quotas getQuotas(QuotaRoot quotaRoot) {
        MaxQuotaManager.QuotaDetails quotaDetails = maxQuotaManager.quotaDetails(quotaRoot);
        CurrentQuotas currentQuotas = Mono.from(currentQuotaManager.getCurrentQuotas(quotaRoot)).block().sanitize();
        return new Quotas(
            Quota.<QuotaCountLimit, QuotaCountUsage>builder()
                .used(currentQuotas.count())
                .computedLimit(maxQuotaManager.getMaxMessage(quotaDetails.getMaxMessageDetails()).orElse(QuotaCountLimit.unlimited()))
                .limitsByScope(quotaDetails.getMaxMessageDetails())
                .build(),
            Quota.<QuotaSizeLimit, QuotaSizeUsage>builder()
                .used(currentQuotas.size())
                .computedLimit(maxQuotaManager.getMaxStorage(quotaDetails.getMaxStorageDetails()).orElse(QuotaSizeLimit.unlimited()))
                .limitsByScope(quotaDetails.getMaxStorageDetails())
                .build());
    }

    @Override
    public Publisher<Quotas> getQuotasReactive(QuotaRoot quotaRoot) {
        return Mono.zip(
                Mono.from(maxQuotaManager.quotaDetailsReactive(quotaRoot)),
                Mono.from(currentQuotaManager.getCurrentQuotas(quotaRoot)).map(CurrentQuotas::sanitize))
            .map(tuple -> new Quotas(
                Quota.<QuotaCountLimit, QuotaCountUsage>builder()
                    .used(tuple.getT2().count())
                    .computedLimit(maxQuotaManager.getMaxMessage(tuple.getT1().getMaxMessageDetails()).orElse(QuotaCountLimit.unlimited()))
                    .limitsByScope(tuple.getT1().getMaxMessageDetails())
                    .build(),
                Quota.<QuotaSizeLimit, QuotaSizeUsage>builder()
                    .used(tuple.getT2().size())
                    .computedLimit(maxQuotaManager.getMaxStorage(tuple.getT1().getMaxStorageDetails()).orElse(QuotaSizeLimit.unlimited()))
                    .limitsByScope(tuple.getT1().getMaxStorageDetails())
                    .build()));
    }
}