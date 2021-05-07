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

import com.github.steveash.guavate.Guavate;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
        perUserQuota.setMaxStorage(quotaRoot, maxStorageQuota).block();
    }

    @Override
    public void setMaxMessage(QuotaRoot quotaRoot, QuotaCountLimit maxMessageCount) {
        perUserQuota.setMaxMessage(quotaRoot, maxMessageCount).block();
    }

    @Override
    public void setDomainMaxMessage(Domain domain, QuotaCountLimit count) {
        perDomainQuota.setMaxMessage(domain, count).block();
    }

    @Override
    public void setDomainMaxStorage(Domain domain, QuotaSizeLimit size) {
        perDomainQuota.setMaxStorage(domain, size).block();
    }

    @Override
    public void removeDomainMaxMessage(Domain domain) {
        perDomainQuota.removeMaxMessage(domain).block();
    }

    @Override
    public void removeDomainMaxStorage(Domain domain) {
        perDomainQuota.removeMaxStorage(domain).block();
    }

    @Override
    public Optional<QuotaCountLimit> getDomainMaxMessage(Domain domain) {
        return perDomainQuota.getMaxMessage(domain).blockOptional();
    }

    @Override
    public Optional<QuotaSizeLimit> getDomainMaxStorage(Domain domain) {
        return perDomainQuota.getMaxStorage(domain).blockOptional();
    }

    @Override
    public void removeMaxMessage(QuotaRoot quotaRoot) {
        perUserQuota.removeMaxMessage(quotaRoot).block();
    }

    @Override
    public void removeMaxStorage(QuotaRoot quotaRoot) {
        perUserQuota.removeMaxStorage(quotaRoot).block();
    }

    @Override
    public void setGlobalMaxStorage(QuotaSizeLimit globalMaxStorage) {
        globalQuota.setGlobalMaxStorage(globalMaxStorage).block();
    }

    @Override
    public void removeGlobalMaxStorage() {
        globalQuota.removeGlobaltMaxStorage().block();
    }

    @Override
    public void setGlobalMaxMessage(QuotaCountLimit globalMaxMessageCount) {
        globalQuota.setGlobalMaxMessage(globalMaxMessageCount).block();
    }

    @Override
    public void removeGlobalMaxMessage() {
        globalQuota.removeGlobalMaxMessage().block();
    }

    @Override
    public Optional<QuotaSizeLimit> getGlobalMaxStorage() {
        return globalQuota.getGlobalMaxStorage().blockOptional();
    }

    @Override
    public Optional<QuotaCountLimit> getGlobalMaxMessage() {
        return globalQuota.getGlobalMaxMessage().blockOptional();
    }

    @Override
    public Map<Quota.Scope, QuotaCountLimit> listMaxMessagesDetails(QuotaRoot quotaRoot) {
        return listMaxMessagesDetailsReactive(quotaRoot).block();
    }

    @Override
    public Mono<Map<Quota.Scope, QuotaCountLimit>> listMaxMessagesDetailsReactive(QuotaRoot quotaRoot) {
        return Flux.merge(
            perUserQuota.getMaxMessage(quotaRoot)
                .map(limit -> Pair.of(Quota.Scope.User, limit)),
            Mono.justOrEmpty(quotaRoot.getDomain())
                .flatMap(perDomainQuota::getMaxMessage)
                .map(limit -> Pair.of(Quota.Scope.Domain, limit)),
            globalQuota.getGlobalMaxMessage()
                .map(limit -> Pair.of(Quota.Scope.Global, limit)))
            .collect(Guavate.toImmutableMap(
                Pair::getKey,
                Pair::getValue));
    }

    @Override
    public Map<Quota.Scope, QuotaSizeLimit> listMaxStorageDetails(QuotaRoot quotaRoot) {
        return listMaxStorageDetailsReactive(quotaRoot).block();
    }

    @Override
    public Mono<Map<Quota.Scope, QuotaSizeLimit>> listMaxStorageDetailsReactive(QuotaRoot quotaRoot) {
        return Flux.merge(
            perUserQuota.getMaxStorage(quotaRoot)
                .map(limit -> Pair.of(Quota.Scope.User, limit)),
            Mono.justOrEmpty(quotaRoot.getDomain())
                .flatMap(perDomainQuota::getMaxStorage)
                .map(limit -> Pair.of(Quota.Scope.Domain, limit)),
            globalQuota.getGlobalMaxStorage()
                .map(limit -> Pair.of(Quota.Scope.Global, limit)))
            .collect(Guavate.toImmutableMap(
                Pair::getKey,
                Pair::getValue));
    }

    @Override
    public QuotaDetails quotaDetails(QuotaRoot quotaRoot) {
        return Mono.zip(
                perUserQuota.getLimits(quotaRoot),
                Mono.justOrEmpty(quotaRoot.getDomain()).flatMap(perDomainQuota::getLimits).switchIfEmpty(Mono.just(Limits.empty())),
                globalQuota.getGlobalMaxStorage().map(Optional::of).switchIfEmpty(Mono.just(Optional.empty())),
                globalQuota.getGlobalMaxMessage().map(Optional::of).switchIfEmpty(Mono.just(Optional.empty())))
            .map(tuple -> new QuotaDetails(
                countDetails(tuple.getT1(), tuple.getT2(), tuple.getT4()),
                sizeDetails(tuple.getT1(), tuple.getT2(), tuple.getT3())))
            .block();
    }

    private Map<Quota.Scope, QuotaSizeLimit> sizeDetails(Limits userLimits, Limits domainLimits, Optional<QuotaSizeLimit> globalLimits) {
        return Stream.of(
                userLimits.getSizeLimit().stream().map(limit -> Pair.of(Quota.Scope.User, limit)),
                domainLimits.getSizeLimit().stream().map(limit -> Pair.of(Quota.Scope.Domain, limit)),
                globalLimits.stream().map(limit -> Pair.of(Quota.Scope.Global, limit)))
            .flatMap(Function.identity())
            .collect(Guavate.toImmutableMap(
                Pair::getKey,
                Pair::getValue));
    }

    private Map<Quota.Scope, QuotaCountLimit> countDetails(Limits userLimits, Limits domainLimits, Optional<QuotaCountLimit> globalLimits) {
        return Stream.of(
                userLimits.getCountLimit().stream().map(limit -> Pair.of(Quota.Scope.User, limit)),
                domainLimits.getCountLimit().stream().map(limit -> Pair.of(Quota.Scope.Domain, limit)),
                globalLimits.stream().map(limit -> Pair.of(Quota.Scope.Global, limit)))
            .flatMap(Function.identity())
            .collect(Guavate.toImmutableMap(
                Pair::getKey,
                Pair::getValue));
    }
}
