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

package org.apache.james.domainlist.memory;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.apache.james.core.Domain;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.domainlist.lib.DomainListConfiguration;

import com.google.common.collect.ImmutableList;

// TODO Fix RecipientRewriteTableProcessorTest
// TODO Fix JamesMailetContextContract
public class MemoryDomainList implements DomainList {
    private final List<Domain> domains;
    private final DomainListConfiguration configuration;

    @Inject
    public MemoryDomainList() {
        this(DomainListConfiguration.DEFAULT);
    }

    @Inject
    public MemoryDomainList(DomainListConfiguration configuration) {
        this.domains = new ArrayList<>();
        this.configuration = configuration;
    }

    @Override
    public List<Domain> getDomains() {
        return ImmutableList.copyOf(domains);
    }

    @Override
    public boolean containsDomain(Domain domain) {
        return domains.contains(domain);
    }

    @Override
    public void addDomain(Domain domain) throws DomainListException {
        if (containsDomain(domain)) {
            throw new DomainListException(domain.name() + " already exists.");
        }
        domains.add(domain);
    }

    @Override
    public void removeDomain(Domain domain) throws DomainListException {
        if (!domains.remove(domain)) {
            throw new DomainListException(domain.name() + " was not found");
        }
    }

    @Override
    public Domain getDefaultDomain() throws DomainListException {
        return configuration.getDefaultDomain();
    }
}
