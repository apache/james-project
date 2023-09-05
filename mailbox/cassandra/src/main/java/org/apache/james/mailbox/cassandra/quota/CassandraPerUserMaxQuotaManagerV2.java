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

import static org.apache.james.backends.cassandra.components.CassandraQuotaLimitDao.QuotaLimitKey;
import static org.apache.james.util.ReactorUtils.publishIfPresent;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.cassandra.components.CassandraQuotaLimitDao;
import org.apache.james.core.Domain;
import org.apache.james.core.quota.QuotaComponent;
import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaLimit;
import org.apache.james.core.quota.QuotaScope;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.core.quota.QuotaType;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.MaxQuotaManager;

import com.google.common.collect.ImmutableMap;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
public class CassandraPerUserMaxQuotaManagerV2 implements MaxQuotaManager {

    private static final String GLOBAL_IDENTIFIER = "global";

    private final CassandraQuotaLimitDao cassandraQuotaLimitDao;

    @Inject
    public CassandraPerUserMaxQuotaManagerV2(CassandraQuotaLimitDao cassandraQuotaLimitDao) {
        this.cassandraQuotaLimitDao = cassandraQuotaLimitDao;
    }

    @Override
    public void setMaxStorage(QuotaRoot quotaRoot, QuotaSizeLimit maxStorageQuota) {
        setMaxStorageReactive(quotaRoot, maxStorageQuota).block();
    }

    @Override
    public Mono<Void> setMaxStorageReactive(QuotaRoot quotaRoot, QuotaSizeLimit maxStorageQuota) {
        return cassandraQuotaLimitDao.setQuotaLimit(QuotaLimit.builder()
                .quotaScope(QuotaScope.USER)
                .identifier(quotaRoot.getValue())
                .quotaComponent(QuotaComponent.MAILBOX)
                .quotaType(QuotaType.SIZE)
                .quotaLimit(QuotaCodec.quotaValueToLong(maxStorageQuota))
                .build());
    }

    @Override
    public void setMaxMessage(QuotaRoot quotaRoot, QuotaCountLimit maxMessageCount) {
        setMaxMessageReactive(quotaRoot, maxMessageCount).block();
    }

    @Override
    public Mono<Void> setMaxMessageReactive(QuotaRoot quotaRoot, QuotaCountLimit maxMessageCount) {
        return cassandraQuotaLimitDao.setQuotaLimit(QuotaLimit.builder()
                .quotaScope(QuotaScope.USER)
                .identifier(quotaRoot.getValue())
                .quotaComponent(QuotaComponent.MAILBOX)
                .quotaType(QuotaType.COUNT)
                .quotaLimit(QuotaCodec.quotaValueToLong(maxMessageCount))
                .build());
    }

    @Override
    public void setDomainMaxMessage(Domain domain, QuotaCountLimit count) {
        setDomainMaxMessageReactive(domain, count).block();
    }

    @Override
    public Mono<Void> setDomainMaxMessageReactive(Domain domain, QuotaCountLimit count) {
        return cassandraQuotaLimitDao.setQuotaLimit(QuotaLimit.builder()
                .quotaScope(QuotaScope.DOMAIN)
                .identifier(domain.asString())
                .quotaComponent(QuotaComponent.MAILBOX)
                .quotaType(QuotaType.COUNT)
                .quotaLimit(QuotaCodec.quotaValueToLong(count))
                .build());
    }

    @Override
    public void setDomainMaxStorage(Domain domain, QuotaSizeLimit size) {
        setDomainMaxStorageReactive(domain, size).block();
    }

    @Override
    public Mono<Void> setDomainMaxStorageReactive(Domain domain, QuotaSizeLimit size) {
        return cassandraQuotaLimitDao.setQuotaLimit(QuotaLimit.builder()
                .quotaScope(QuotaScope.DOMAIN)
                .identifier(domain.asString())
                .quotaComponent(QuotaComponent.MAILBOX)
                .quotaType(QuotaType.SIZE)
                .quotaLimit(QuotaCodec.quotaValueToLong(size))
                .build());
    }

    @Override
    public void removeDomainMaxMessage(Domain domain) {
        removeDomainMaxMessageReactive(domain).block();
    }

    @Override
    public Mono<Void> removeDomainMaxMessageReactive(Domain domain) {
        return cassandraQuotaLimitDao.deleteQuotaLimit(QuotaLimitKey.of(QuotaComponent.MAILBOX, QuotaScope.DOMAIN, domain.asString(), QuotaType.COUNT));
    }

    @Override
    public void removeDomainMaxStorage(Domain domain) {
        removeDomainMaxStorageReactive(domain).block();
    }

    @Override
    public Mono<Void> removeDomainMaxStorageReactive(Domain domain) {
        return cassandraQuotaLimitDao.deleteQuotaLimit(QuotaLimitKey.of(QuotaComponent.MAILBOX, QuotaScope.DOMAIN, domain.asString(), QuotaType.SIZE));
    }

    @Override
    public Optional<QuotaCountLimit> getDomainMaxMessage(Domain domain) {
        return getDomainMaxMessageReactive(domain).blockOptional();
    }

    @Override
    public Mono<QuotaCountLimit> getDomainMaxMessageReactive(Domain domain) {
        return getMaxMessageReactive(QuotaScope.DOMAIN, domain.asString());
    }

    @Override
    public Optional<QuotaSizeLimit> getDomainMaxStorage(Domain domain) {
        return getDomainMaxStorageReactive(domain).blockOptional();
    }

    @Override
    public Mono<QuotaSizeLimit> getDomainMaxStorageReactive(Domain domain) {
        return getMaxStorageReactive(QuotaScope.DOMAIN, domain.asString());
    }

    @Override
    public void removeMaxMessage(QuotaRoot quotaRoot) {
        removeMaxMessageReactive(quotaRoot).block();
    }

    @Override
    public Mono<Void> removeMaxMessageReactive(QuotaRoot quotaRoot) {
        return cassandraQuotaLimitDao.deleteQuotaLimit(QuotaLimitKey.of(QuotaComponent.MAILBOX, QuotaScope.USER, quotaRoot.getValue(), QuotaType.COUNT));
    }

    @Override
    public void removeMaxStorage(QuotaRoot quotaRoot) {
        removeMaxStorageReactive(quotaRoot).block();
    }

    @Override
    public Mono<Void> removeMaxStorageReactive(QuotaRoot quotaRoot) {
        return cassandraQuotaLimitDao.deleteQuotaLimit(QuotaLimitKey.of(QuotaComponent.MAILBOX, QuotaScope.USER, quotaRoot.getValue(), QuotaType.SIZE));
    }

    @Override
    public void setGlobalMaxStorage(QuotaSizeLimit globalMaxStorage) {
        setGlobalMaxStorageReactive(globalMaxStorage).block();
    }

    @Override
    public Mono<Void> setGlobalMaxStorageReactive(QuotaSizeLimit globalMaxStorage) {
        return cassandraQuotaLimitDao.setQuotaLimit(QuotaLimit.builder()
                .quotaScope(QuotaScope.GLOBAL).identifier(GLOBAL_IDENTIFIER)
                .quotaComponent(QuotaComponent.MAILBOX)
                .quotaType(QuotaType.SIZE)
                .quotaLimit(QuotaCodec.quotaValueToLong(globalMaxStorage))
                .build());
    }

    @Override
    public void removeGlobalMaxStorage() {
        removeGlobalMaxStorageReactive().block();
    }

    @Override
    public Mono<Void> removeGlobalMaxStorageReactive() {
        return cassandraQuotaLimitDao.deleteQuotaLimit(QuotaLimitKey.of(QuotaComponent.MAILBOX, QuotaScope.GLOBAL, GLOBAL_IDENTIFIER, QuotaType.SIZE));
    }

    @Override
    public void setGlobalMaxMessage(QuotaCountLimit globalMaxMessageCount) {
        setGlobalMaxMessageReactive(globalMaxMessageCount).block();
    }

    @Override
    public Mono<Void> setGlobalMaxMessageReactive(QuotaCountLimit globalMaxMessageCount) {
        return cassandraQuotaLimitDao.setQuotaLimit(QuotaLimit.builder()
                .quotaScope(QuotaScope.GLOBAL).identifier(GLOBAL_IDENTIFIER)
                .quotaComponent(QuotaComponent.MAILBOX)
                .quotaType(QuotaType.COUNT)
                .quotaLimit(QuotaCodec.quotaValueToLong(globalMaxMessageCount))
                .build());
    }

    @Override
    public void removeGlobalMaxMessage() {
        removeGlobalMaxMessageReactive().block();
    }

    @Override
    public Mono<Void> removeGlobalMaxMessageReactive() {
        return cassandraQuotaLimitDao.deleteQuotaLimit(QuotaLimitKey.of(QuotaComponent.MAILBOX, QuotaScope.GLOBAL, GLOBAL_IDENTIFIER, QuotaType.COUNT));
    }

    @Override
    public Optional<QuotaSizeLimit> getGlobalMaxStorage() {
        return getGlobalMaxStorageReactive().blockOptional();
    }

    @Override
    public Mono<QuotaSizeLimit> getGlobalMaxStorageReactive() {
        return getMaxStorageReactive(QuotaScope.GLOBAL, GLOBAL_IDENTIFIER);
    }

    @Override
    public Optional<QuotaCountLimit> getGlobalMaxMessage() {
        return getGlobalMaxMessageReactive().blockOptional();
    }

    @Override
    public Mono<QuotaCountLimit> getGlobalMaxMessageReactive() {
        return getMaxMessageReactive(QuotaScope.GLOBAL, GLOBAL_IDENTIFIER);
    }

    @Override
    public Map<Quota.Scope, QuotaCountLimit> listMaxMessagesDetails(QuotaRoot quotaRoot) {
        return listMaxMessagesDetailsReactive(quotaRoot).block();
    }

    @Override
    public Mono<Map<Quota.Scope, QuotaCountLimit>> listMaxMessagesDetailsReactive(QuotaRoot quotaRoot) {
        return Flux.merge(
            getMaxMessageReactive(QuotaScope.USER, quotaRoot.getValue())
                .map(limit -> Pair.of(Quota.Scope.User, limit)),
            Mono.justOrEmpty(quotaRoot.getDomain())
                .flatMap(domain -> getMaxMessageReactive(QuotaScope.DOMAIN, domain.asString()))
                .map(limit -> Pair.of(Quota.Scope.Domain, limit)),
            getGlobalMaxMessageReactive()
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
            getMaxStorageReactive(QuotaScope.USER, quotaRoot.getValue())
                .map(limit -> Pair.of(Quota.Scope.User, limit)),
            Mono.justOrEmpty(quotaRoot.getDomain())
                .flatMap(domain -> getMaxStorageReactive(QuotaScope.DOMAIN, domain.asString()))
                .map(limit -> Pair.of(Quota.Scope.Domain, limit)),
            getGlobalMaxStorageReactive()
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
            getLimits(QuotaScope.USER, quotaRoot.getValue()),
            Mono.justOrEmpty(quotaRoot.getDomain()).flatMap(domain -> getLimits(QuotaScope.DOMAIN, domain.asString())).switchIfEmpty(Mono.just(Limits.empty())),
            getLimits(QuotaScope.GLOBAL, GLOBAL_IDENTIFIER))
            .map(tuple -> new QuotaDetails(
                countDetails(tuple.getT1(), tuple.getT2(), tuple.getT3().getCountLimit()),
                sizeDetails(tuple.getT1(), tuple.getT2(), tuple.getT3().getSizeLimit())));
    }

    private Mono<Limits> getLimits(QuotaScope quotaScope, String identifier) {
        return cassandraQuotaLimitDao.getQuotaLimits(QuotaComponent.MAILBOX, quotaScope, identifier)
            .collectList()
            .map(list -> {
                Map<QuotaType, Optional<Long>> map = list.stream().collect(Collectors.toMap(QuotaLimit::getQuotaType, QuotaLimit::getQuotaLimit));
                return new Limits(
                    map.getOrDefault(QuotaType.SIZE, Optional.empty()).flatMap(QuotaCodec::longToQuotaSize),
                    map.getOrDefault(QuotaType.COUNT, Optional.empty()).flatMap(QuotaCodec::longToQuotaCount));
            }).switchIfEmpty(Mono.just(Limits.empty()));
    }

    private Mono<QuotaCountLimit> getMaxMessageReactive(QuotaScope quotaScope, String identifier) {
        return cassandraQuotaLimitDao.getQuotaLimit(QuotaLimitKey.of(QuotaComponent.MAILBOX, quotaScope, identifier, QuotaType.COUNT))
            .map(QuotaLimit::getQuotaLimit)
            .handle(publishIfPresent())
            .map(QuotaCodec::longToQuotaCount)
            .handle(publishIfPresent());
    }

    public Mono<QuotaSizeLimit> getMaxStorageReactive(QuotaScope quotaScope, String identifier) {
        return cassandraQuotaLimitDao.getQuotaLimit(QuotaLimitKey.of(QuotaComponent.MAILBOX, quotaScope, identifier, QuotaType.SIZE))
            .map(QuotaLimit::getQuotaLimit)
            .handle(publishIfPresent())
            .map(QuotaCodec::longToQuotaSize)
            .handle(publishIfPresent());
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
