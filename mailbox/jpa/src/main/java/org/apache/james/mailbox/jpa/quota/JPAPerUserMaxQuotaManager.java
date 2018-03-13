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

package org.apache.james.mailbox.jpa.quota;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.mailbox.quota.QuotaCount;
import org.apache.james.mailbox.quota.QuotaSize;

import com.github.steveash.guavate.Guavate;

public class JPAPerUserMaxQuotaManager implements MaxQuotaManager {

    private final JPAPerUserMaxQuotaDAO dao;

    @Inject
    public JPAPerUserMaxQuotaManager(JPAPerUserMaxQuotaDAO dao) {
        this.dao = dao;
    }

    @Override
    public void setMaxStorage(QuotaRoot quotaRoot, QuotaSize maxStorageQuota) {
        dao.setMaxStorage(quotaRoot, Optional.of(maxStorageQuota));
    }

    @Override
    public void setMaxMessage(QuotaRoot quotaRoot, QuotaCount maxMessageCount) {
        dao.setMaxMessage(quotaRoot, Optional.of(maxMessageCount));
    }

    @Override
    public void removeMaxMessage(QuotaRoot quotaRoot) {
        dao.setMaxMessage(quotaRoot, Optional.empty());
    }

    @Override
    public void setDefaultMaxStorage(QuotaSize defaultMaxStorage) {
        dao.setDefaultMaxStorage(Optional.of(defaultMaxStorage));
    }

    @Override
    public void removeDefaultMaxMessage() {
        dao.setDefaultMaxMessage(Optional.empty());
    }

    @Override
    public void setDefaultMaxMessage(QuotaCount defaultMaxMessageCount) {
        dao.setDefaultMaxMessage(Optional.of(defaultMaxMessageCount));
    }

    @Override
    public Optional<QuotaSize> getDefaultMaxStorage() {
        return dao.getDefaultMaxStorage();
    }

    @Override
    public Optional<QuotaCount> getDefaultMaxMessage() {
        return dao.getDefaultMaxMessage();
    }

    @Override
    public Optional<QuotaSize> getMaxStorage(QuotaRoot quotaRoot) {
        return dao.getMaxStorage(quotaRoot)
            .map(Optional::of)
            .orElseGet(this::getDefaultMaxStorage);
    }

    @Override
    public Optional<QuotaCount> getMaxMessage(QuotaRoot quotaRoot) {
        return dao.getMaxMessage(quotaRoot)
                .map(Optional::of)
                .orElseGet(this::getDefaultMaxMessage);
    }

    @Override
    public Map<Quota.Scope, QuotaCount> listMaxMessagesDetails(QuotaRoot quotaRoot) {
        return Stream.of(
            Pair.of(Quota.Scope.User, dao.getMaxMessage(quotaRoot)),
            Pair.of(Quota.Scope.Global, dao.getDefaultMaxMessage()))
        .filter(pair -> pair.getValue().isPresent())
        .collect(Guavate.toImmutableMap(Pair::getKey, value -> value.getValue().get()));
    }

    @Override
    public Map<Quota.Scope, QuotaSize> listMaxStorageDetails(QuotaRoot quotaRoot) {
        return Stream.of(
            Pair.of(Quota.Scope.User, dao.getMaxStorage(quotaRoot)),
            Pair.of(Quota.Scope.Global, dao.getDefaultMaxStorage()))
        .filter(pair -> pair.getValue().isPresent())
        .collect(Guavate.toImmutableMap(Pair::getKey, value -> value.getValue().get()));
    }

    @Override
    public void removeMaxStorage(QuotaRoot quotaRoot) {
        dao.setMaxStorage(quotaRoot, Optional.empty());
    }

    @Override
    public void removeDefaultMaxStorage() {
        dao.setDefaultMaxStorage(Optional.empty());
    }

}
