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
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.core.Domain;
import org.apache.james.core.quota.QuotaCount;
import org.apache.james.core.quota.QuotaSize;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.util.OptionalUtils;

import com.github.fge.lambdas.Throwing;
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
    public void setDomainMaxMessage(Domain domain, QuotaCount count) {
        dao.setDomainMaxMessage(domain, Optional.of(count));
    }

    @Override
    public void setDomainMaxStorage(Domain domain, QuotaSize size) {
        dao.setDomainMaxStorage(domain, Optional.of(size));
    }

    @Override
    public void removeDomainMaxMessage(Domain domain) {
        dao.setDomainMaxMessage(domain, Optional.empty());
    }

    @Override
    public void removeDomainMaxStorage(Domain domain) {
        dao.setDomainMaxStorage(domain, Optional.empty());
    }

    @Override
    public Optional<QuotaCount> getDomainMaxMessage(Domain domain) {
        return dao.getDomainMaxMessage(domain);
    }

    @Override
    public Optional<QuotaSize> getDomainMaxStorage(Domain domain) {
        return dao.getDomainMaxStorage(domain);
    }

    @Override
    public void removeMaxMessage(QuotaRoot quotaRoot) {
        dao.setMaxMessage(quotaRoot, Optional.empty());
    }

    @Override
    public void setGlobalMaxStorage(QuotaSize globalMaxStorage) {
        dao.setGlobalMaxStorage(Optional.of(globalMaxStorage));
    }

    @Override
    public void removeGlobalMaxMessage() {
        dao.setGlobalMaxMessage(Optional.empty());
    }

    @Override
    public void setGlobalMaxMessage(QuotaCount globalMaxMessageCount) {
        dao.setGlobalMaxMessage(Optional.of(globalMaxMessageCount));
    }

    @Override
    public Optional<QuotaSize> getGlobalMaxStorage() {
        return dao.getGlobalMaxStorage();
    }

    @Override
    public Optional<QuotaCount> getGlobalMaxMessage() {
        return dao.getGlobalMaxMessage();
    }

    @Override
    public Optional<QuotaSize> getMaxStorage(QuotaRoot quotaRoot) {
        return Stream
            .of((Supplier<Optional<QuotaSize>>) () -> dao.getMaxStorage(quotaRoot),
                () -> quotaRoot.getDomain().flatMap(this::getDomainMaxStorage),
                this::getGlobalMaxStorage)
            .flatMap(supplier -> OptionalUtils.toStream(supplier.get()))
            .findFirst();
    }

    @Override
    public Optional<QuotaCount> getMaxMessage(QuotaRoot quotaRoot) {
        return Stream
            .of((Supplier<Optional<QuotaCount>>) () -> dao.getMaxMessage(quotaRoot),
                () -> quotaRoot.getDomain().flatMap(this::getDomainMaxMessage),
                this::getGlobalMaxMessage)
            .flatMap(supplier -> OptionalUtils.toStream(supplier.get()))
            .findFirst();
    }

    @Override
    public Map<Quota.Scope, QuotaCount> listMaxMessagesDetails(QuotaRoot quotaRoot) {
        Function<Domain, Optional<QuotaCount>> domainQuotaFunction = Throwing.function(this::getDomainMaxMessage).sneakyThrow();
        return Stream.of(
            Pair.of(Quota.Scope.User, dao.getMaxMessage(quotaRoot)),
            Pair.of(Quota.Scope.Domain, quotaRoot.getDomain().flatMap(domainQuotaFunction)),
            Pair.of(Quota.Scope.Global, dao.getGlobalMaxMessage()))
        .filter(pair -> pair.getValue().isPresent())
        .collect(Guavate.toImmutableMap(Pair::getKey, value -> value.getValue().get()));
    }

    @Override
    public Map<Quota.Scope, QuotaSize> listMaxStorageDetails(QuotaRoot quotaRoot) {
        Function<Domain, Optional<QuotaSize>> domainQuotaFunction = Throwing.function(this::getDomainMaxStorage).sneakyThrow();
        return Stream.of(
            Pair.of(Quota.Scope.User, dao.getMaxStorage(quotaRoot)),
            Pair.of(Quota.Scope.Domain, quotaRoot.getDomain().flatMap(domainQuotaFunction)),
            Pair.of(Quota.Scope.Global, dao.getGlobalMaxStorage()))
        .filter(pair -> pair.getValue().isPresent())
        .collect(Guavate.toImmutableMap(Pair::getKey, value -> value.getValue().get()));
    }

    @Override
    public void removeMaxStorage(QuotaRoot quotaRoot) {
        dao.setMaxStorage(quotaRoot, Optional.empty());
    }

    @Override
    public void removeGlobalMaxStorage() {
        dao.setGlobalMaxStorage(Optional.empty());
    }

}
