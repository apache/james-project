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

package org.apache.james.mailbox.cassandra.quota;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.mailbox.quota.QuotaCount;
import org.apache.james.mailbox.quota.QuotaSize;

import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;

public class CassandraPerUserMaxQuotaManager implements MaxQuotaManager {

    private final CassandraPerUserMaxQuotaDao perUserQuota;
    private final CassandraPerDomainMaxQuotaDao perDomainQuota;
    private final CassandraDefaultMaxQuotaDao defaultQuota;

    @Inject
    public CassandraPerUserMaxQuotaManager(CassandraPerUserMaxQuotaDao perUserQuota,
                                           CassandraPerDomainMaxQuotaDao domainQuota,
                                           CassandraDefaultMaxQuotaDao defaultQuota) {
        this.perUserQuota = perUserQuota;
        this.perDomainQuota = domainQuota;
        this.defaultQuota = defaultQuota;
    }

    @Override
    public void setMaxStorage(QuotaRoot quotaRoot, QuotaSize maxStorageQuota) {
        perUserQuota.setMaxStorage(quotaRoot, maxStorageQuota);
    }

    @Override
    public void setMaxMessage(QuotaRoot quotaRoot, QuotaCount maxMessageCount) {
        perUserQuota.setMaxMessage(quotaRoot, maxMessageCount);
    }

    @Override
    public void setDomainMaxMessage(String domain, QuotaCount count) {
        perDomainQuota.setMaxMessage(domain, count);
    }

    @Override
    public void setDomainMaxStorage(String domain, QuotaSize size) {
        perDomainQuota.setMaxStorage(domain, size);
    }

    @Override
    public void removeDomainMaxMessage(String domain) throws MailboxException {
        perDomainQuota.removeMaxMessage(domain);
    }

    @Override
    public void removeDomainMaxStorage(String domain) {
        perDomainQuota.removeMaxStorage(domain);
    }

    @Override
    public Optional<QuotaCount> getDomainMaxMessage(String domain) {
        return perDomainQuota.getMaxMessage(domain);
    }

    @Override
    public Optional<QuotaSize> getDomainMaxStorage(String domain) {
        return perDomainQuota.getMaxStorage(domain);
    }

    @Override
    public void removeMaxMessage(QuotaRoot quotaRoot) {
        perUserQuota.removeMaxMessage(quotaRoot);
    }

    @Override
    public void removeMaxStorage(QuotaRoot quotaRoot) {
        perUserQuota.removeMaxStorage(quotaRoot);
    }

    @Override
    public void setDefaultMaxStorage(QuotaSize defaultMaxStorage) {
        defaultQuota.setDefaultMaxStorage(defaultMaxStorage);
    }

    @Override
    public void removeDefaultMaxStorage() {
        defaultQuota.removeDefaultMaxStorage();
    }

    @Override
    public void setDefaultMaxMessage(QuotaCount defaultMaxMessageCount) {
        defaultQuota.setDefaultMaxMessage(defaultMaxMessageCount);
    }

    @Override
    public void removeDefaultMaxMessage() {
        defaultQuota.removeDefaultMaxMessage();
    }

    @Override
    public Optional<QuotaSize> getDefaultMaxStorage() {
        return defaultQuota.getDefaultMaxStorage();
    }

    @Override
    public Optional<QuotaCount> getDefaultMaxMessage() {
        return defaultQuota.getDefaultMaxMessage();
    }

    @Override
    public Optional<QuotaSize> getMaxStorage(QuotaRoot quotaRoot) {
        return perUserQuota.getMaxStorage(quotaRoot)
            .map(Optional::of)
            .orElseGet(Throwing.supplier(this::getDefaultMaxStorage).sneakyThrow());
    }

    @Override
    public Optional<QuotaCount> getMaxMessage(QuotaRoot quotaRoot) {
        return perUserQuota.getMaxMessage(quotaRoot)
            .map(Optional::of)
            .orElseGet(Throwing.supplier(this::getDefaultMaxMessage).sneakyThrow());
    }

    @Override
    public Map<Quota.Scope, QuotaCount> listMaxMessagesDetails(QuotaRoot quotaRoot) {
        return Stream.of(
                Pair.of(Quota.Scope.User, perUserQuota.getMaxMessage(quotaRoot)),
                Pair.of(Quota.Scope.Global, defaultQuota.getDefaultMaxMessage()))
            .filter(pair -> pair.getValue().isPresent())
            .collect(Guavate.toImmutableMap(Pair::getKey, value -> value.getValue().get()));
    }

    @Override
    public Map<Quota.Scope, QuotaSize> listMaxStorageDetails(QuotaRoot quotaRoot) {
        return Stream.of(
                Pair.of(Quota.Scope.User, perUserQuota.getMaxStorage(quotaRoot)),
                Pair.of(Quota.Scope.Global, defaultQuota.getDefaultMaxStorage()))
            .filter(pair -> pair.getValue().isPresent())
            .collect(Guavate.toImmutableMap(Pair::getKey, value -> value.getValue().get()));
    }
}
