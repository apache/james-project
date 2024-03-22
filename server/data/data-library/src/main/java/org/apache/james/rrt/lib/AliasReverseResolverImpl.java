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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import jakarta.inject.Inject;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.rrt.api.AliasReverseResolver;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.util.StreamUtils;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;

public class AliasReverseResolverImpl implements AliasReverseResolver {
    private final RecipientRewriteTable recipientRewriteTable;

    @Inject
    public AliasReverseResolverImpl(RecipientRewriteTable recipientRewriteTable) {
        this.recipientRewriteTable = recipientRewriteTable;
    }

    @Override
    public Stream<MailAddress> listAddresses(Username user) throws RecipientRewriteTable.ErrorMappingException, RecipientRewriteTableException {
        CanSendFromImpl.DomainFetcher domains = domainFetcher(user);

        return relatedAliases(user)
            .flatMap(allowedUser -> domains.fetch(allowedUser)
                .stream()
                .map(Optional::of)
                .map(allowedUser::withOtherDomain)
                .map(Throwing.function(Username::asMailAddress).sneakyThrow()))
            .distinct();
    }

    private Stream<Username> relatedAliases(Username user) {
        return StreamUtils.iterate(
            user,
            getMappingLimit(),
            Throwing.<Username, Stream<Username>>function(targetUser ->
                recipientRewriteTable
                    .listSources(Mapping.alias(targetUser.asString()))
                    .map(MappingSource::asUsername)
                    .flatMap(Optional::stream)).sneakyThrow()
        );
    }

    private CanSendFromImpl.DomainFetcher domainFetcher(Username user) {
        HashMap<Domain, List<Domain>> fetchedDomains = new HashMap<>();
        List<Domain> userDomains = relatedDomains(user).collect(ImmutableList.toImmutableList());
        user.getDomainPart().ifPresent(domain -> fetchedDomains.put(domain, userDomains));
        Function<Domain, List<Domain>> computeDomain = givenDomain -> Stream.concat(userDomains.stream(), fetchDomains(givenDomain)).collect(ImmutableList.toImmutableList());
        return givenUsername ->
            givenUsername
                .getDomainPart()
                .map(domain -> fetchedDomains.computeIfAbsent(domain, computeDomain))
                .orElseGet(Arrays::asList);
    }

    private Stream<Domain> relatedDomains(Username user) {
        return user.getDomainPart()
            .map(this::fetchDomains)
            .orElseGet(Stream::empty);
    }

    private Stream<Domain> fetchDomains(Domain domain) {
        return StreamUtils.iterate(
            domain,
            getMappingLimit(),
            Throwing.<Domain, Stream<Domain>>function(targetDomain ->
                recipientRewriteTable
                    .listSources(Mapping.domainAlias(targetDomain))
                    .map(MappingSource::asDomain)
                    .flatMap(Optional::stream)).sneakyThrow()
        );
    }

    private long getMappingLimit() {
        return recipientRewriteTable.getConfiguration().getMappingLimit();
    }
}
