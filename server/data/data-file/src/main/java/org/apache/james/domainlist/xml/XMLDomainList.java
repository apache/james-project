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

package org.apache.james.domainlist.xml;

import java.util.ArrayList;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.core.Domain;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.domainlist.lib.AbstractDomainList;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.lifecycle.api.Configurable;

/**
 * Mimic the old behavior of JAMES
 */
@Singleton
public class XMLDomainList extends AbstractDomainList implements Configurable {

    private final List<Domain> domainNames = new ArrayList<>();
    private boolean isConfigured = false;

    @Inject
    public XMLDomainList(DNSService dns) {
        super(dns);
    }

    @Override
    public void configure(DomainListConfiguration domainListConfiguration) throws ConfigurationException {
        super.configure(domainListConfiguration);
        isConfigured = true;
    }

    @Override
    protected List<Domain> getDomainListInternal() {
        return new ArrayList<>(domainNames);
    }

    @Override
    protected boolean containsDomainInternal(Domain domain) throws DomainListException {
        return domainNames.contains(domain);
    }

    @Override
    public void addDomain(Domain domain) throws DomainListException {
        if (isConfigured) {
            throw new DomainListException("Read-Only DomainList implementation");
        }
        domainNames.add(domain);
    }

    @Override
    public void doRemoveDomain(Domain domain) throws DomainListException {
        if (isConfigured) {
            throw new DomainListException("Read-Only DomainList implementation");
        }
        domainNames.remove(domain);
    }

}
