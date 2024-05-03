/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ***************************************************************/

package org.apache.james.rrt.lib;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.rrt.api.AliasReverseResolver;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.util.ReactorUtils;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;

import reactor.core.publisher.Flux;

public class AliasReverseResolverImpl implements AliasReverseResolver {
    private final RecipientRewriteTable recipientRewriteTable;

    @Inject
    public AliasReverseResolverImpl(RecipientRewriteTable recipientRewriteTable) {
        this.recipientRewriteTable = recipientRewriteTable;
    }

    @Override
    public Flux<MailAddress> listAddresses(Username user) {
        CanSendFromImpl.DomainFetcher domains = domainFetcher();

        return relatedAliases(user)
            .flatMap(allowedUser -> user.getDomainPart()
                .map(domain -> Flux.concat(
                    Flux.just(allowedUser),
                    domains.fetch(domain).map(allowedUser::withOtherDomain)))
                .orElseGet(() -> Flux.just(allowedUser)))
            .map(Throwing.function(Username::asMailAddress).sneakyThrow())
            .distinct();
    }

    private Flux<Username> relatedAliases(Username user) {
        Pair<Username, Integer> userWithDepth = Pair.of(user, 0);
        return Flux.just(userWithDepth)
            .expand(value -> {
                if (value.getRight() >= getMappingLimit()) {
                    return Flux.empty();
                }
                return recipientRewriteTable.listSourcesReactive(Mapping.alias(value.getLeft().asString()))
                    .map(MappingSource::asUsername)
                    .handle(ReactorUtils.publishIfPresent())
                    .map(u -> Pair.of(u, value.getRight() + 1));
            }).map(Pair::getLeft);
    }

    private CanSendFromImpl.DomainFetcher domainFetcher() {
        return new CanSendFromImpl.DomainFetcher() {
            private final Map<Domain, List<Domain>> memoized = new ConcurrentHashMap<>();
            @Override
            public Flux<Domain> fetch(Domain domain) {
                if (memoized.containsKey(domain)) {
                    return Flux.fromIterable(memoized.get(domain));
                }
                return fetchDomains(domain)
                    .collect(Collectors.toList())
                    .doOnNext(next -> memoized.put(domain, next))
                    .flatMapIterable(i -> i);
            }
        };
    }

    private Flux<Domain> fetchDomains(Domain domain) {
        Pair<Domain, Integer> domainWithDepth = Pair.of(domain, 0);
        Flux<Pair<Domain, Integer>> flux = Flux.just(domainWithDepth);
        return expandDomains(flux);
    }

    private Flux<Domain> expandDomains(Flux<Pair<Domain, Integer>> flux) {
        return flux.expand(value -> {
                Preconditions.checkArgument(value.getRight() < getMappingLimit());
                return recipientRewriteTable.listSourcesReactive(Mapping.domainAlias(value.getKey()))
                    .map(MappingSource::asDomain)
                    .handle(ReactorUtils.publishIfPresent())
                    .map(u -> Pair.of(u, value.getRight() + 1));
            }).map(Pair::getLeft);
    }

    private long getMappingLimit() {
        return recipientRewriteTable.getConfiguration().getMappingLimit();
    }
}
