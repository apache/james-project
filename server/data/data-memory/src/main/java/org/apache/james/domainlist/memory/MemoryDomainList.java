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

import jakarta.inject.Inject;

import org.apache.james.core.Domain;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.domainlist.lib.AbstractDomainList;

import com.google.common.collect.ImmutableList;

public class MemoryDomainList extends AbstractDomainList {

    private final List<Domain> domains;

    @Inject
    public MemoryDomainList(DNSService dns) {
        super(dns);
        this.domains = new ArrayList<>();
    }

    @Override
    protected List<Domain> getDomainListInternal() {
        return ImmutableList.copyOf(domains);
    }

    @Override
    protected boolean containsDomainInternal(Domain domain) {
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
    public void doRemoveDomain(Domain domain) throws DomainListException {
        if (!domains.remove(domain)) {
            throw new DomainListException(domain.name() + " was not found");
        }
    }
}
