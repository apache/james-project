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

import javax.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaCount;
import org.apache.james.core.quota.QuotaSize;
import org.apache.james.mailbox.exception.MailboxException;
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
import com.github.steveash.guavate.Guavate;
import com.google.common.collect.Sets;

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
        try {
            QuotaRoot quotaRoot = userQuotaRootResolver.forUser(username);
            if (quota.getCount().isPresent()) {
                maxQuotaManager.setMaxMessage(quotaRoot, quota.getCount().get());
            } else {
                maxQuotaManager.removeMaxMessage(quotaRoot);
            }

            if (quota.getSize().isPresent()) {
                maxQuotaManager.setMaxStorage(quotaRoot, quota.getSize().get());
            } else {
                maxQuotaManager.removeMaxStorage(quotaRoot);
            }
        } catch (MailboxException e) {
            throw new RuntimeException(e);
        }
    }

    public QuotaDetailsDTO getQuota(Username username) throws MailboxException {
        QuotaRoot quotaRoot = userQuotaRootResolver.forUser(username);
        QuotaDetailsDTO.Builder quotaDetails = QuotaDetailsDTO.builder()
            .occupation(quotaManager.getStorageQuota(quotaRoot),
                quotaManager.getMessageQuota(quotaRoot));

        mergeMaps(
                maxQuotaManager.listMaxMessagesDetails(quotaRoot),
                maxQuotaManager.listMaxStorageDetails(quotaRoot))
            .forEach(quotaDetails::valueForScope);

        quotaDetails.computed(computedQuota(quotaRoot));
        return quotaDetails.build();
    }

    private ValidatedQuotaDTO computedQuota(QuotaRoot quotaRoot) throws MailboxException {
        return ValidatedQuotaDTO
                .builder()
                .count(maxQuotaManager.getMaxMessage(quotaRoot))
                .size(maxQuotaManager.getMaxStorage(quotaRoot))
                .build();
    }

    private Map<Quota.Scope, ValidatedQuotaDTO> mergeMaps(Map<Quota.Scope, QuotaCount> counts, Map<Quota.Scope, QuotaSize> sizes) {
       return Sets.union(counts.keySet(), sizes.keySet())
            .stream()
            .collect(Collectors.toMap(Function.identity(),
                scope -> ValidatedQuotaDTO
                            .builder()
                            .count(Optional.ofNullable(counts.get(scope)))
                            .size(Optional.ofNullable(sizes.get(scope)))
                            .build()));
    }


    public Optional<QuotaSize> getMaxSizeQuota(Username username) throws MailboxException {
        return maxQuotaManager.getMaxStorage(userQuotaRootResolver.forUser(username));
    }

    public void defineMaxSizeQuota(Username username, QuotaSize quotaSize) throws MailboxException {
        maxQuotaManager.setMaxStorage(userQuotaRootResolver.forUser(username), quotaSize);
    }

    public void deleteMaxSizeQuota(Username username) throws MailboxException {
        maxQuotaManager.removeMaxStorage(userQuotaRootResolver.forUser(username));
    }

    public Optional<QuotaCount> getMaxCountQuota(Username username) throws MailboxException {
        return maxQuotaManager.getMaxMessage(userQuotaRootResolver.forUser(username));
    }

    public void defineMaxCountQuota(Username username, QuotaCount quotaCount) throws MailboxException {
        maxQuotaManager.setMaxMessage(userQuotaRootResolver.forUser(username), quotaCount);
    }

    public void deleteMaxCountQuota(Username username) throws MailboxException {
        maxQuotaManager.removeMaxMessage(userQuotaRootResolver.forUser(username));
    }

    public List<UsersQuotaDetailsDTO> getUsersQuota(QuotaQuery quotaQuery) {
        return quotaSearcher.search(quotaQuery)
            .stream()
            .map(Throwing.function(user -> UsersQuotaDetailsDTO.builder()
                .user(user)
                .detail(getQuota(user))
                .build()))
            .collect(Guavate.toImmutableList());
    }
}
