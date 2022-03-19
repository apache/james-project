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

package org.apache.james.mailetcontainer.impl;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.inject.Inject;

import jakarta.mail.internet.ParseException;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.mailetcontainer.api.LocalResources;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.lib.Mapping;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;

public class LocalResourcesImpl implements LocalResources {
    private static final EnumSet<Mapping.Type> ALIAS_TYPES = EnumSet.of(Mapping.Type.Alias, Mapping.Type.DomainAlias);
    public static final Logger LOGGER = LoggerFactory.getLogger(LocalResourcesImpl.class);

    private final UsersRepository localUsers;
    private final DomainList domains;
    private final RecipientRewriteTable recipientRewriteTable;

    @Inject
    public LocalResourcesImpl(UsersRepository localUsers, DomainList domains, RecipientRewriteTable recipientRewriteTable) {
        this.localUsers = localUsers;
        this.domains = domains;
        this.recipientRewriteTable = recipientRewriteTable;
    }

    @Override
    public boolean isLocalServer(Domain domain) {
        try {
            return domains.containsDomain(domain);
        } catch (DomainListException e) {
            throw new RuntimeException("Unable to retrieve domains", e);
        }
    }

    @Override
    public boolean isLocalUser(String name) {
        if (name == null) {
            return false;
        }
        try {
            MailAddress mailAddress = Username.of(name).withDefaultDomain(domains.getDefaultDomain()).asMailAddress();
            return isLocalEmail(mailAddress);
        } catch (DomainListException e) {
            throw new RuntimeException("Unable to retrieve domains", e);
        } catch (ParseException e) {
            throw new RuntimeException("Unable to parse mail address", e);
        }
    }

    @Override
    public boolean isLocalEmail(MailAddress mailAddress) {
        if (mailAddress != null) {
            if (!isLocalServer(mailAddress.getDomain())) {
                return false;
            }
            return belongsToALocalUser(mailAddress);
        }
        return false;
    }

    private boolean belongsToALocalUser(MailAddress mailAddress) {
        try {
            return isLocaluser(mailAddress)
                || isLocalAlias(mailAddress);
        } catch (UsersRepositoryException e) {
            throw new RuntimeException("Unable to retrieve users", e);
        } catch (RecipientRewriteTable.ErrorMappingException | RecipientRewriteTableException e) {
            throw new RuntimeException("Unable to retrieve RRTs", e);
        }
    }

    @Override
    public Collection<MailAddress> localEmails(Collection<MailAddress> mailAddresses) {
        return addressByDomains(mailAddresses)
            .flatMap(this::hasLocalDomain)
            .filter(this::belongsToALocalUser)
            .collect(ImmutableList.toImmutableList());
    }

    private Stream<MailAddress> hasLocalDomain(Map.Entry<Domain, Collection<MailAddress>> entry) {
        if (isLocalServer(entry.getKey())) {
            return entry.getValue().stream();
        }
        return Stream.empty();
    }

    private Stream<Map.Entry<Domain, Collection<MailAddress>>> addressByDomains(Collection<MailAddress> mailAddresses) {
        return mailAddresses.stream()
            .collect(ImmutableListMultimap.toImmutableListMultimap(
                MailAddress::getDomain, Function.identity()))
            .asMap()
            .entrySet()
            .stream();
    }

    private boolean isLocaluser(MailAddress mailAddress) throws UsersRepositoryException {
        return localUsers.contains(localUsers.getUsername(mailAddress));
    }

    private boolean isLocalAlias(MailAddress mailAddress) throws UsersRepositoryException, RecipientRewriteTable.ErrorMappingException, RecipientRewriteTableException {
        return recipientRewriteTable.getResolvedMappings(mailAddress.getLocalPart(), mailAddress.getDomain(), ALIAS_TYPES)
            .asStream()
            .map(mapping -> mapping.asMailAddress()
                .orElseThrow(() -> new IllegalStateException(String.format("Can not compute address for mapping %s", mapping.asString()))))
            .filter(Throwing.predicate(this::isLocaluser).sneakyThrow())
            .peek(ownerAddress -> LOGGER.debug("{} belongs to {} local user", mailAddress.asString(), ownerAddress.asString()))
            .findFirst()
            .isPresent();
    }
}
