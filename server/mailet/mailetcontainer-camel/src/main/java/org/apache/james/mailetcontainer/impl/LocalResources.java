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

import java.util.EnumSet;

import javax.inject.Inject;
import javax.mail.internet.ParseException;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.lib.Mapping;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;

import com.github.fge.lambdas.Throwing;

class LocalResources {
    private static final EnumSet<Mapping.Type> ALIAS_TYPES = EnumSet.of(Mapping.Type.Alias, Mapping.Type.DomainAlias);

    private final UsersRepository localUsers;
    private final DomainList domains;
    private final RecipientRewriteTable recipientRewriteTable;

    @Inject
    LocalResources(UsersRepository localUsers, DomainList domains, RecipientRewriteTable recipientRewriteTable) {
        this.localUsers = localUsers;
        this.domains = domains;
        this.recipientRewriteTable = recipientRewriteTable;
    }

    boolean isLocalServer(Domain domain) {
        try {
            return domains.containsDomain(domain);
        } catch (DomainListException e) {
            throw new RuntimeException("Unable to retrieve domains", e);
        }
    }

    boolean isLocalUser(String name) {
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

    boolean isLocalEmail(MailAddress mailAddress) {
        if (mailAddress != null) {
            if (!isLocalServer(mailAddress.getDomain())) {
                return false;
            }
            try {
                return isLocaluser(mailAddress)
                    || isLocalAlias(mailAddress);
            } catch (UsersRepositoryException e) {
                throw new RuntimeException("Unable to retrieve users", e);
            } catch (RecipientRewriteTable.ErrorMappingException | RecipientRewriteTableException e) {
                throw new RuntimeException("Unable to retrieve RRTs", e);
            }
        }
        return false;
    }

    private boolean isLocaluser(MailAddress mailAddress) throws UsersRepositoryException {
        return localUsers.contains(localUsers.getUsername(mailAddress));
    }

    private boolean isLocalAlias(MailAddress mailAddress) throws UsersRepositoryException, RecipientRewriteTable.ErrorMappingException, RecipientRewriteTableException {
        return recipientRewriteTable.getResolvedMappings(mailAddress.getLocalPart(), mailAddress.getDomain(), ALIAS_TYPES)
            .asStream()
            .map(mapping -> mapping.asMailAddress()
                .orElseThrow(() -> new IllegalStateException(String.format("Can not compute address for mapping %s", mapping.asString()))))
            .anyMatch(Throwing.predicate(this::isLocaluser).sneakyThrow());
    }
}
