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

package org.apache.james.domainlist.lib;

import java.util.List;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.Domain;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.lifecycle.api.Configurable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public abstract class LoggingDomainList implements DomainList {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingDomainList.class);

    private final DomainList underlying;

    public LoggingDomainList(DomainList underlying) {
        this.underlying = underlying;
    }

    @Override
    public ImmutableList<Domain> getDomains() throws DomainListException {
        ImmutableSet<Domain> allDomains = ImmutableSet.copyOf(underlying.getDomains());

        if (LOGGER.isDebugEnabled()) {
            for (Domain domain : allDomains) {
                LOGGER.debug("Handling mail for: " + domain.name());
            }
        }

        return ImmutableList.copyOf(allDomains);
    }

    @Override
    public boolean containsDomain(Domain domain) throws DomainListException {
        return underlying.containsDomain(domain);
    }

    @Override
    public void addDomain(Domain domain) throws DomainListException {
        underlying.addDomain(domain);
    }

    @Override
    public void removeDomain(Domain domain) throws DomainListException {
        underlying.removeDomain(domain);
    }

    @Override
    public Domain getDefaultDomain() throws DomainListException {
        return underlying.getDefaultDomain();
    }
}
