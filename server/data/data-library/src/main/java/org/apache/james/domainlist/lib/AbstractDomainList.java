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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.Domain;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.api.AutoDetectedDomainRemovalException;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.lifecycle.api.Configurable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;

/**
 * All implementations of the DomainList interface should extends this abstract
 * class
 */
public abstract class AbstractDomainList implements DomainList, Configurable {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractDomainList.class);

    private DomainListConfiguration configuration;
    private Domain defaultDomain;

    public AbstractDomainList() {

    }

    // TODO kill meeeee
    public AbstractDomainList(DNSService dns) {

    }

    @Override
    public void configure(HierarchicalConfiguration<ImmutableNode> config) throws ConfigurationException {
        DomainListConfiguration domainListConfiguration = DomainListConfiguration.from(config);

        configure(domainListConfiguration);
    }

    public void configure(DomainListConfiguration domainListConfiguration) throws ConfigurationException {
        this.configuration = domainListConfiguration;

        configureDefaultDomain(domainListConfiguration.getDefaultDomain());

        addConfiguredDomains(domainListConfiguration.getConfiguredDomains());
    }
    
    public void configure(DomainListConfiguration.Builder configurationBuilder) throws ConfigurationException {
        configure(configurationBuilder.build());
    }

    protected void addConfiguredDomains(List<Domain> domains) {
        domains.stream()
            .filter(Throwing.predicate((Domain domain) -> !containsDomainInternal(domain)).sneakyThrow())
            .forEach(Throwing.consumer(this::addDomain).sneakyThrow());
    }

    @VisibleForTesting void configureDefaultDomain(Domain defaultDomain) throws ConfigurationException {
        try {
            setDefaultDomain(defaultDomain);

            String hostName = InetAddress.getLocalHost().getHostName();
            if (mayChangeDefaultDomain()) {
                setDefaultDomain(Domain.of(hostName));
            }
        } catch (UnknownHostException e) {
            LOGGER.warn("Unable to retrieve hostname.", e);
        } catch (DomainListException e) {
            LOGGER.error("An error occured while creating the default domain", e);
        }
    }

    private boolean mayChangeDefaultDomain() {
        return configuration.isAutoDetect() && Domain.LOCALHOST.equals(defaultDomain);
    }

    private void setDefaultDomain(Domain defaultDomain) throws DomainListException {
        if (defaultDomain != null && !containsDomain(defaultDomain)) {
            addDomain(defaultDomain);
        }
        this.defaultDomain = defaultDomain;
    }

    @Override
    public Domain getDefaultDomain() throws DomainListException {
        if (defaultDomain != null) {
            return defaultDomain;
        } else {
            throw new DomainListException("Null default domain. Domain list might not be configured yet.");
        }
    }

    @Override
    public boolean containsDomain(Domain domain) throws DomainListException {
        return containsDomainInternal(domain);
    }

    @Override
    public ImmutableList<Domain> getDomains() throws DomainListException {
        ImmutableSet<Domain> allDomains = ImmutableSet.copyOf(getDomainListInternal());

        if (LOGGER.isDebugEnabled()) {
            for (Domain domain : allDomains) {
                LOGGER.debug("Handling mail for: " + domain.name());
            }
        }

        return ImmutableList.copyOf(allDomains);
    }

    @Override
    public void removeDomain(Domain domain) throws DomainListException {
        doRemoveDomain(domain);
    }

    /**
     * Return domainList
     * 
     * @return List
     */
    protected abstract List<Domain> getDomainListInternal() throws DomainListException;

    protected abstract boolean containsDomainInternal(Domain domain) throws DomainListException;

    protected abstract void doRemoveDomain(Domain domain) throws DomainListException;

}
