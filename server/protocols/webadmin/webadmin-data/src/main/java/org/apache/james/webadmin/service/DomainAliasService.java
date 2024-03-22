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

package org.apache.james.webadmin.service;

import jakarta.inject.Inject;

import org.apache.james.core.Domain;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.api.SameSourceAndDestinationException;
import org.apache.james.rrt.lib.Mapping;
import org.apache.james.rrt.lib.MappingSource;
import org.apache.james.webadmin.dto.DomainAliasResponse;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableSet;

public class DomainAliasService {
    public static class DomainNotFound extends RuntimeException {
        private final Domain domain;

        DomainNotFound(Domain domain) {
            this.domain = domain;
        }

        public Domain getDomain() {
            return domain;
        }
    }

    @FunctionalInterface
    interface MappingOperation {
        void perform(MappingSource mappingSource, Mapping mapping) throws RecipientRewriteTableException;
    }

    private final RecipientRewriteTable recipientRewriteTable;
    private final DomainList domainList;

    @Inject
    public DomainAliasService(RecipientRewriteTable recipientRewriteTable, DomainList domainList) {
        this.recipientRewriteTable = recipientRewriteTable;
        this.domainList = domainList;
    }

    public void removeCorrespondingDomainAliases(Domain domain) throws RecipientRewriteTableException {
        MappingSource mappingSource = MappingSource.fromDomain(domain);
        recipientRewriteTable.getStoredMappings(mappingSource)
            .asStream()
            .forEach(Throwing.<Mapping>consumer(mapping -> recipientRewriteTable.removeMapping(mappingSource, mapping)).sneakyThrow());
    }

    public ImmutableSet<DomainAliasResponse> listDomainAliases(Domain domain) throws RecipientRewriteTableException {
        return recipientRewriteTable.listSources(Mapping.domainAlias(domain))
            .map(DomainAliasResponse::new)
            .collect(ImmutableSet.toImmutableSet());
    }

    public boolean hasAliases(Domain domain) throws DomainListException, RecipientRewriteTableException {
        return domainList.containsDomain(domain)
            || recipientRewriteTable.listSources(Mapping.domainAlias(domain)).findFirst().isPresent();
    }

    public void addDomainAlias(Domain sourceDomain, Domain destinationDomain) throws DomainListException, RecipientRewriteTableException {
        performOperationOnAlias(recipientRewriteTable::addMapping, sourceDomain, destinationDomain);
    }

    public void removeDomainAlias(Domain sourceDomain, Domain destinationDomain) throws DomainListException, RecipientRewriteTableException {
        performOperationOnAlias(recipientRewriteTable::removeMapping, sourceDomain, destinationDomain);
    }

    private void performOperationOnAlias(MappingOperation operation, Domain sourceDomain, Domain destinationDomain) throws DomainListException, RecipientRewriteTableException {
        if (!domainList.containsDomain(sourceDomain)) {
            throw new DomainNotFound(sourceDomain);
        }

        checkSameSourceAndDestination(sourceDomain, destinationDomain);
        operation.perform(MappingSource.fromDomain(sourceDomain), Mapping.domainAlias(destinationDomain));
    }

    private void checkSameSourceAndDestination(Domain source, Domain destination) throws RecipientRewriteTableException {
        if (source.equals(destination)) {
            throw new SameSourceAndDestinationException("Source and destination domain can't be the same!");
        }
    }
}
