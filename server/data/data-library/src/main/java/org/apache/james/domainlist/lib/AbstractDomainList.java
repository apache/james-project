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

    enum DomainType {
        DefaultDomain,
        Internal,
        Detected,
        DetectedIp
    }

    private final DNSService dns;
    private DomainListConfiguration configuration;
    private Domain defaultDomain;

    public AbstractDomainList(DNSService dns) {
        this.dns = dns;
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
        boolean internalAnswer = containsDomainInternal(domain);
        return internalAnswer || detectedDomainsContains(domain);
    }

    private boolean detectedDomainsContains(Domain domain) throws DomainListException {
        if (configuration.isAutoDetect() || configuration.isAutoDetectIp()) {
            return getDomains().contains(domain);
        }
        return false;
    }

    @Override
    public ImmutableList<Domain> getDomains() throws DomainListException {
        Multimap<DomainType, Domain> domainsWithType = getDomainsWithType();
        ImmutableSet<Domain> allDomains = domainsWithType.values()
            .stream()
            .collect(ImmutableSet.toImmutableSet());

        if (LOGGER.isDebugEnabled()) {
            for (Domain domain : allDomains) {
                LOGGER.debug("Handling mail for: " + domain.name());
            }
        }

        return ImmutableList.copyOf(allDomains);
    }

    private Multimap<DomainType, Domain> getDomainsWithType() throws DomainListException {
        List<Domain> domains = getDomainListInternal();
        ImmutableList<Domain> detectedDomains = detectDomains();

        ImmutableList<Domain> domainsWithoutIp = ImmutableList.<Domain>builder()
            .addAll(domains)
            .addAll(detectedDomains)
            .build();
        ImmutableList<Domain> ips = detectIps(domainsWithoutIp);

        ImmutableMultimap.Builder<DomainType, Domain> result = ImmutableMultimap.<DomainType, Domain>builder()
            .putAll(DomainType.Internal, domains)
            .putAll(DomainType.Detected, detectedDomains)
            .putAll(DomainType.DetectedIp, ips);
        Optional.ofNullable(defaultDomain)
            .ifPresent(domain -> result.put(DomainType.DefaultDomain, domain));
        return result.build();
    }

    private ImmutableList<Domain> detectIps(Collection<Domain> domains) {
        if (configuration.isAutoDetectIp()) {
            return getDomainsIpStream(domains, dns, LOGGER)
                .collect(ImmutableList.toImmutableList());
        }
        return ImmutableList.of();
    }

    private ImmutableList<Domain> detectDomains() {
        if (configuration.isAutoDetect()) {
            String hostName;
            try {
                hostName = removeTrailingDot(dns.getHostName(dns.getLocalHost()));
            } catch (UnknownHostException ue) {
                hostName = "localhost";
            }

            LOGGER.info("Local host is: {}", hostName);
            if (!Strings.isNullOrEmpty(hostName) && !hostName.equals("localhost")) {
                return ImmutableList.of(Domain.of(hostName));
            }
        }
        return ImmutableList.of();
    }

    private String removeTrailingDot(String domain) {
        if (domain != null && domain.endsWith(".")) {
            return domain.substring(0, domain.length() - 1);
        }
        return domain;
    }

    /**
     * Return a stream of all IP addresses of the given domains.
     * 
     * @param domains
     *            Iterable of domains
     * @return Stream of ipaddress for domains
     */
    private static Stream<Domain> getDomainsIpStream(Collection<Domain> domains, DNSService dns, Logger log) {
        return domains.stream()
            .flatMap(domain -> getDomainIpStream(domain, dns, log))
            .distinct();
    }

    private static Stream<Domain> getDomainIpStream(Domain domain, DNSService dns, Logger log) {
        try {
            return dns.getAllByName(domain.name()).stream()
                .map(InetAddress::getHostAddress)
                .map(Domain::of)
                .distinct();
        } catch (UnknownHostException e) {
            log.error("Cannot get IP address(es) for {}", domain);
            return Stream.of();
        }
    }

    @Override
    public void removeDomain(Domain domain) throws DomainListException {
        if (isAutoDetected(domain)) {
            throw new AutoDetectedDomainRemovalException(domain);
        }

        doRemoveDomain(domain);
    }

    private boolean isAutoDetected(Domain domain) throws DomainListException {
        Multimap<DomainType, Domain> domainsWithType = getDomainsWithType();

        return domainsWithType.get(DomainType.Detected).contains(domain)
            || domainsWithType.get(DomainType.DetectedIp).contains(domain)
            || domainsWithType.get(DomainType.DefaultDomain).contains(domain);
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
