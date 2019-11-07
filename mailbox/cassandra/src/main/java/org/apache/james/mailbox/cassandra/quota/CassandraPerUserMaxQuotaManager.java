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
import java.util.function.Function;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.core.Domain;
import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.MaxQuotaManager;

import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;

public class CassandraPerUserMaxQuotaManager implements MaxQuotaManager {

    private final CassandraPerUserMaxQuotaDao perUserQuota;
    private final CassandraPerDomainMaxQuotaDao perDomainQuota;
    private final CassandraGlobalMaxQuotaDao globalQuota;

    @Inject
    public CassandraPerUserMaxQuotaManager(CassandraPerUserMaxQuotaDao perUserQuota,
                                           CassandraPerDomainMaxQuotaDao domainQuota,
                                           CassandraGlobalMaxQuotaDao globalQuota) {
        this.perUserQuota = perUserQuota;
        this.perDomainQuota = domainQuota;
        this.globalQuota = globalQuota;
    }

    @Override
    public void setMaxStorage(QuotaRoot quotaRoot, QuotaSizeLimit maxStorageQuota) {
        perUserQuota.setMaxStorage(quotaRoot, maxStorageQuota);
    }

    @Override
    public void setMaxMessage(QuotaRoot quotaRoot, QuotaCountLimit maxMessageCount) {
        perUserQuota.setMaxMessage(quotaRoot, maxMessageCount);
    }

    @Override
    public void setDomainMaxMessage(Domain domain, QuotaCountLimit count) {
        perDomainQuota.setMaxMessage(domain, count);
    }

    @Override
    public void setDomainMaxStorage(Domain domain, QuotaSizeLimit size) {
        perDomainQuota.setMaxStorage(domain, size);
    }

    @Override
    public void removeDomainMaxMessage(Domain domain) {
        perDomainQuota.removeMaxMessage(domain);
    }

    @Override
    public void removeDomainMaxStorage(Domain domain) {
        perDomainQuota.removeMaxStorage(domain);
    }

    @Override
    public Optional<QuotaCountLimit> getDomainMaxMessage(Domain domain) {
        return perDomainQuota.getMaxMessage(domain);
    }

    @Override
    public Optional<QuotaSizeLimit> getDomainMaxStorage(Domain domain) {
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
    public void setGlobalMaxStorage(QuotaSizeLimit globalMaxStorage) {
        globalQuota.setGlobalMaxStorage(globalMaxStorage);
    }

    @Override
    public void removeGlobalMaxStorage() {
        globalQuota.removeGlobaltMaxStorage();
    }

    @Override
    public void setGlobalMaxMessage(QuotaCountLimit globalMaxMessageCount) {
        globalQuota.setGlobalMaxMessage(globalMaxMessageCount);
    }

    @Override
    public void removeGlobalMaxMessage() {
        globalQuota.removeGlobalMaxMessage();
    }

    @Override
    public Optional<QuotaSizeLimit> getGlobalMaxStorage() {
        return globalQuota.getGlobalMaxStorage();
    }

    @Override
    public Optional<QuotaCountLimit> getGlobalMaxMessage() {
        return globalQuota.getGlobalMaxMessage();
    }

    @Override
    public Map<Quota.Scope, QuotaCountLimit> listMaxMessagesDetails(QuotaRoot quotaRoot) {
        Function<Domain, Optional<QuotaCountLimit>> domainQuotaSupplier = Throwing.function(this::getDomainMaxMessage).sneakyThrow();
        return Stream.of(
                Pair.of(Quota.Scope.User, perUserQuota.getMaxMessage(quotaRoot)),
                Pair.of(Quota.Scope.Domain, quotaRoot.getDomain().flatMap(domainQuotaSupplier)),
                Pair.of(Quota.Scope.Global, globalQuota.getGlobalMaxMessage()))
            .filter(pair -> pair.getValue().isPresent())
            .collect(Guavate.toImmutableMap(Pair::getKey, value -> value.getValue().get()));
    }

    @Override
    public Map<Quota.Scope, QuotaSizeLimit> listMaxStorageDetails(QuotaRoot quotaRoot) {
        Function<Domain, Optional<QuotaSizeLimit>> domainQuotaSupplier = Throwing.function(this::getDomainMaxStorage).sneakyThrow();
        return Stream.of(
                Pair.of(Quota.Scope.User, perUserQuota.getMaxStorage(quotaRoot)),
                Pair.of(Quota.Scope.Domain, quotaRoot.getDomain().flatMap(domainQuotaSupplier)),
                Pair.of(Quota.Scope.Global, globalQuota.getGlobalMaxStorage()))
            .filter(pair -> pair.getValue().isPresent())
            .collect(Guavate.toImmutableMap(Pair::getKey, value -> value.getValue().get()));
    }
}
