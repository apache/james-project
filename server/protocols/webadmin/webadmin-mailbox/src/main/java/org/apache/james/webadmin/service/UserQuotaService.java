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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.UserQuotaRootResolver;
import org.apache.james.quota.search.QuotaQuery;
import org.apache.james.quota.search.QuotaSearcher;
import org.apache.james.webadmin.dto.QuotaDetailsDTO;
import org.apache.james.webadmin.dto.UsersQuotaDetailsDTO;
import org.apache.james.webadmin.dto.ValidatedQuotaDTO;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

import reactor.core.publisher.Mono;

public class UserQuotaService {

    private final MaxQuotaManager maxQuotaManager;
    private final QuotaManager quotaManager;
    private final UserQuotaRootResolver userQuotaRootResolver;
    private final QuotaSearcher quotaSearcher;

    @Inject
    public UserQuotaService(MaxQuotaManager maxQuotaManager, QuotaManager quotaManager, UserQuotaRootResolver userQuotaRootResolver, QuotaSearcher quotaSearcher) {
        this.maxQuotaManager = maxQuotaManager;
        this.quotaManager = quotaManager;
        this.userQuotaRootResolver = userQuotaRootResolver;
        this.quotaSearcher = quotaSearcher;
    }

    public void defineQuota(Username username, ValidatedQuotaDTO quota) {
        QuotaRoot quotaRoot = userQuotaRootResolver.forUser(username);
        defineUserMaxMessage(quotaRoot, quota)
            .then(defineUserMaxStorage(quotaRoot, quota))
            .block();
    }

    private Mono<Void> defineUserMaxMessage(QuotaRoot quotaRoot, ValidatedQuotaDTO quota) {
        return quota.getCount()
            .map(countLimit -> Mono.from(maxQuotaManager.setMaxMessageReactive(quotaRoot, countLimit)))
            .orElseGet(() -> Mono.from(maxQuotaManager.removeMaxMessageReactive(quotaRoot)));
    }

    private Mono<Void> defineUserMaxStorage(QuotaRoot quotaRoot, ValidatedQuotaDTO quota) {
        return quota.getSize()
            .map(sizeLimit -> Mono.from(maxQuotaManager.setMaxStorageReactive(quotaRoot, sizeLimit)))
            .orElseGet(() -> Mono.from(maxQuotaManager.removeMaxStorageReactive(quotaRoot)));
    }

    public QuotaDetailsDTO getQuota(Username username) {
        return getQuota(userQuotaRootResolver.forUser(username));
    }

    private QuotaDetailsDTO getQuota(QuotaRoot quotaRoot) {
        return Mono.zip(
            Mono.from(quotaManager.getQuotasReactive(quotaRoot)),
            Mono.from(maxQuotaManager.listMaxMessagesDetailsReactive(quotaRoot)),
            Mono.from(maxQuotaManager.listMaxStorageDetailsReactive(quotaRoot)))
            .map(tuple3 -> QuotaDetailsDTO.builder()
                    .occupation(tuple3.getT1().getStorageQuota(),
                        tuple3.getT1().getMessageQuota())
                    .computed(ValidatedQuotaDTO
                        .builder()
                        .count(maxQuotaManager.getMaxMessage(tuple3.getT2()))
                        .size(maxQuotaManager.getMaxStorage(tuple3.getT3()))
                        .build())
                    .valueForScopes(mergeMaps(tuple3.getT2(), tuple3.getT3()))
                .build())
            .block();
    }

    private Map<Quota.Scope, ValidatedQuotaDTO> mergeMaps(Map<Quota.Scope, QuotaCountLimit> counts, Map<Quota.Scope, QuotaSizeLimit> sizes) {
       return Sets.union(counts.keySet(), sizes.keySet())
            .stream()
            .collect(Collectors.toMap(Function.identity(),
                scope -> ValidatedQuotaDTO
                            .builder()
                            .count(Optional.ofNullable(counts.get(scope)))
                            .size(Optional.ofNullable(sizes.get(scope)))
                            .build()));
    }


    public Optional<QuotaSizeLimit> getMaxSizeQuota(Username username) {
        return Mono.from(maxQuotaManager.listMaxStorageDetailsReactive(userQuotaRootResolver.forUser(username)))
            .map(maxQuotaManager::getMaxStorage)
            .block();
    }

    public void defineMaxSizeQuota(Username username, QuotaSizeLimit quotaSize) {
        Mono.from(maxQuotaManager.setMaxStorageReactive(userQuotaRootResolver.forUser(username), quotaSize)).block();
    }

    public void deleteMaxSizeQuota(Username username) {
        Mono.from(maxQuotaManager.removeMaxStorageReactive(userQuotaRootResolver.forUser(username))).block();
    }

    public Optional<QuotaCountLimit> getMaxCountQuota(Username username) {
        return Mono.from(maxQuotaManager.listMaxMessagesDetailsReactive(userQuotaRootResolver.forUser(username)))
            .map(maxQuotaManager::getMaxMessage)
            .block();
    }

    public void defineMaxCountQuota(Username username, QuotaCountLimit quotaCount) {
        Mono.from(maxQuotaManager.setMaxMessageReactive(userQuotaRootResolver.forUser(username), quotaCount)).block();
    }

    public void deleteMaxCountQuota(Username username) {
        Mono.from(maxQuotaManager.removeMaxMessageReactive(userQuotaRootResolver.forUser(username))).block();
    }

    public List<UsersQuotaDetailsDTO> getUsersQuota(QuotaQuery quotaQuery) {
        return quotaSearcher.search(quotaQuery)
            .stream()
            .map(Throwing.function(user -> UsersQuotaDetailsDTO.builder()
                .user(user)
                .detail(getQuota(userQuotaRootResolver.forMailAddress(user)))
                .build()))
            .collect(ImmutableList.toImmutableList());
    }
}
