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

import com.google.common.collect.ImmutableMap;

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
        setMaxStorageReactive(quotaRoot, maxStorageQuota).block();
    }

    @Override
    public Mono<Void> setMaxStorageReactive(QuotaRoot quotaRoot, QuotaSizeLimit maxStorageQuota) {
        return perUserQuota.setMaxStorage(quotaRoot, maxStorageQuota);
    }

    @Override
    public void setMaxMessage(QuotaRoot quotaRoot, QuotaCountLimit maxMessageCount) {
        setMaxMessageReactive(quotaRoot, maxMessageCount).block();
    }

    @Override
    public Mono<Void> setMaxMessageReactive(QuotaRoot quotaRoot, QuotaCountLimit maxMessageCount) {
        return perUserQuota.setMaxMessage(quotaRoot, maxMessageCount);
    }

    @Override
    public void setDomainMaxMessage(Domain domain, QuotaCountLimit count) {
        setDomainMaxMessageReactive(domain, count).block();
    }

    @Override
    public Mono<Void> setDomainMaxMessageReactive(Domain domain, QuotaCountLimit count) {
        return perDomainQuota.setMaxMessage(domain, count);
    }

    @Override
    public void setDomainMaxStorage(Domain domain, QuotaSizeLimit size) {
        setDomainMaxStorageReactive(domain, size).block();
    }

    @Override
    public Mono<Void> setDomainMaxStorageReactive(Domain domain, QuotaSizeLimit size) {
        return perDomainQuota.setMaxStorage(domain, size);
    }

    @Override
    public void removeDomainMaxMessage(Domain domain) {
        removeDomainMaxMessageReactive(domain).block();
    }

    @Override
    public Mono<Void> removeDomainMaxMessageReactive(Domain domain) {
        return perDomainQuota.removeMaxMessage(domain);
    }

    @Override
    public void removeDomainMaxStorage(Domain domain) {
        removeDomainMaxStorageReactive(domain).block();
    }

    @Override
    public Mono<Void> removeDomainMaxStorageReactive(Domain domain) {
        return perDomainQuota.removeMaxStorage(domain);
    }

    @Override
    public Optional<QuotaCountLimit> getDomainMaxMessage(Domain domain) {
        return getDomainMaxMessageReactive(domain).blockOptional();
    }

    @Override
    public Mono<QuotaCountLimit> getDomainMaxMessageReactive(Domain domain) {
        return perDomainQuota.getMaxMessage(domain);
    }

    @Override
    public Optional<QuotaSizeLimit> getDomainMaxStorage(Domain domain) {
        return getDomainMaxStorageReactive(domain).blockOptional();
    }

    @Override
    public Mono<QuotaSizeLimit> getDomainMaxStorageReactive(Domain domain) {
        return perDomainQuota.getMaxStorage(domain);
    }

    @Override
    public void removeMaxMessage(QuotaRoot quotaRoot) {
        removeMaxMessageReactive(quotaRoot).block();
    }

    @Override
    public Mono<Void> removeMaxMessageReactive(QuotaRoot quotaRoot) {
        return perUserQuota.removeMaxMessage(quotaRoot);
    }

    @Override
    public void removeMaxStorage(QuotaRoot quotaRoot) {
        removeMaxStorageReactive(quotaRoot).block();
    }

    @Override
    public Mono<Void> removeMaxStorageReactive(QuotaRoot quotaRoot) {
        return perUserQuota.removeMaxStorage(quotaRoot);
    }

    @Override
    public void setGlobalMaxStorage(QuotaSizeLimit globalMaxStorage) {
        setGlobalMaxStorageReactive(globalMaxStorage).block();
    }

    @Override
    public Mono<Void> setGlobalMaxStorageReactive(QuotaSizeLimit globalMaxStorage) {
        return globalQuota.setGlobalMaxStorage(globalMaxStorage);
    }

    @Override
    public void removeGlobalMaxStorage() {
        removeGlobalMaxStorageReactive().block();
    }

    @Override
    public Mono<Void> removeGlobalMaxStorageReactive() {
        return globalQuota.removeGlobaltMaxStorage();
    }

    @Override
    public void setGlobalMaxMessage(QuotaCountLimit globalMaxMessageCount) {
        setGlobalMaxMessageReactive(globalMaxMessageCount).block();
    }

    @Override
    public Mono<Void> setGlobalMaxMessageReactive(QuotaCountLimit globalMaxMessageCount) {
        return globalQuota.setGlobalMaxMessage(globalMaxMessageCount);
    }

    @Override
    public void removeGlobalMaxMessage() {
        removeGlobalMaxMessageReactive().block();
    }

    @Override
    public Mono<Void> removeGlobalMaxMessageReactive() {
        return globalQuota.removeGlobalMaxMessage();
    }

    @Override
    public Optional<QuotaSizeLimit> getGlobalMaxStorage() {
        return getGlobalMaxStorageReactive().blockOptional();
    }

    @Override
    public Mono<QuotaSizeLimit> getGlobalMaxStorageReactive() {
        return globalQuota.getGlobalMaxStorage();
    }

    @Override
    public Optional<QuotaCountLimit> getGlobalMaxMessage() {
        return getGlobalMaxMessageReactive().blockOptional();
    }

    @Override
    public Mono<QuotaCountLimit> getGlobalMaxMessageReactive() {
        return globalQuota.getGlobalMaxMessage();
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
            .collect(ImmutableMap.toImmutableMap(
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
            .collect(ImmutableMap.toImmutableMap(
                Pair::getKey,
                Pair::getValue));
    }

    @Override
    public QuotaDetails quotaDetails(QuotaRoot quotaRoot) {
        return quotaDetailsReactive(quotaRoot)
            .block();
    }

    @Override
    public Mono<QuotaDetails> quotaDetailsReactive(QuotaRoot quotaRoot) {
        return Mono.zip(
            perUserQuota.getLimits(quotaRoot),
            Mono.justOrEmpty(quotaRoot.getDomain()).flatMap(perDomainQuota::getLimits).switchIfEmpty(Mono.just(Limits.empty())),
            globalQuota.getGlobalLimits())
            .map(tuple -> new QuotaDetails(
                countDetails(tuple.getT1(), tuple.getT2(), tuple.getT3().getCountLimit()),
                sizeDetails(tuple.getT1(), tuple.getT2(), tuple.getT3().getSizeLimit())));
    }

    private Map<Quota.Scope, QuotaSizeLimit> sizeDetails(Limits userLimits, Limits domainLimits, Optional<QuotaSizeLimit> globalLimits) {
        return Stream.of(
                userLimits.getSizeLimit().stream().map(limit -> Pair.of(Quota.Scope.User, limit)),
                domainLimits.getSizeLimit().stream().map(limit -> Pair.of(Quota.Scope.Domain, limit)),
                globalLimits.stream().map(limit -> Pair.of(Quota.Scope.Global, limit)))
            .flatMap(Function.identity())
            .collect(ImmutableMap.toImmutableMap(
                Pair::getKey,
                Pair::getValue));
    }

    private Map<Quota.Scope, QuotaCountLimit> countDetails(Limits userLimits, Limits domainLimits, Optional<QuotaCountLimit> globalLimits) {
        return Stream.of(
                userLimits.getCountLimit().stream().map(limit -> Pair.of(Quota.Scope.User, limit)),
                domainLimits.getCountLimit().stream().map(limit -> Pair.of(Quota.Scope.Domain, limit)),
                globalLimits.stream().map(limit -> Pair.of(Quota.Scope.Global, limit)))
            .flatMap(Function.identity())
            .collect(ImmutableMap.toImmutableMap(
                Pair::getKey,
                Pair::getValue));
    }
}
