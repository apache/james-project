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

import org.apache.james.core.User;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.mailbox.quota.QuotaCount;
import org.apache.james.mailbox.quota.QuotaSize;
import org.apache.james.mailbox.quota.UserQuotaRootResolver;
import org.apache.james.webadmin.dto.QuotaDTO;

import com.github.fge.lambdas.Throwing;

public class UserQuotaService {

    private final MaxQuotaManager maxQuotaManager;
    private final UserQuotaRootResolver userQuotaRootResolver;

    @Inject
    public UserQuotaService(MaxQuotaManager maxQuotaManager, UserQuotaRootResolver userQuotaRootResolver) {
        this.maxQuotaManager = maxQuotaManager;
        this.userQuotaRootResolver = userQuotaRootResolver;
    }

    public void defineQuota(User user, QuotaDTO quota) {
        QuotaRoot quotaRoot = userQuotaRootResolver.forUser(user);
        quota.getCount()
            .ifPresent(Throwing.consumer(count -> maxQuotaManager.setMaxMessage(quotaRoot, count)));
        quota.getSize()
            .ifPresent(Throwing.consumer(size -> maxQuotaManager.setMaxStorage(quotaRoot, size)));
    }

    public QuotaDTO getQuota(User user) throws MailboxException {
        QuotaRoot quotaRoot = userQuotaRootResolver.forUser(user);
        return QuotaDTO
            .builder()
            .count(maxQuotaManager.getMaxMessage(quotaRoot))
            .size(maxQuotaManager.getMaxStorage(quotaRoot))
            .build();
    }

    public Optional<QuotaSize> getMaxSizeQuota(User user) throws MailboxException {
        return maxQuotaManager.getMaxStorage(userQuotaRootResolver.forUser(user));
    }

    public void defineMaxSizeQuota(User user, QuotaSize quotaSize) throws MailboxException {
        maxQuotaManager.setMaxStorage(userQuotaRootResolver.forUser(user), quotaSize);
    }

    public void deleteMaxSizeQuota(User user) throws MailboxException {
        maxQuotaManager.removeMaxStorage(userQuotaRootResolver.forUser(user));
    }

    public Optional<QuotaCount> getMaxCountQuota(User user) throws MailboxException {
        return maxQuotaManager.getMaxMessage(userQuotaRootResolver.forUser(user));
    }

    public void defineMaxCountQuota(User user, QuotaCount quotaCount) throws MailboxException {
        maxQuotaManager.setMaxMessage(userQuotaRootResolver.forUser(user), quotaCount);
    }

    public void deleteMaxCountQuota(User user) throws MailboxException {
        maxQuotaManager.removeMaxMessage(userQuotaRootResolver.forUser(user));
    }
}
