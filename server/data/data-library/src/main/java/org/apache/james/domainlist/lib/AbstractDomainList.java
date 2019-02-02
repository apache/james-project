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
import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.core.Domain;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.lifecycle.api.Configurable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

/**
 * All implementations of the DomainList interface should extends this abstract
 * class
 */
public abstract class AbstractDomainList implements DomainList, Configurable {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractDomainList.class);

    public static final String CONFIGURE_AUTODETECT = "autodetect";
    public static final String CONFIGURE_AUTODETECT_IP = "autodetectIP";
    public static final String CONFIGURE_DEFAULT_DOMAIN = "defaultDomain";
    public static final String CONFIGURE_DOMAIN_NAMES = "domainnames.domainname";
    public static final String ENV_DOMAIN = "DOMAIN";

    private final DNSService dns;
    private final EnvDetector envDetector;
    private boolean autoDetect = true;
    private boolean autoDetectIP = true;
    private Domain defaultDomain;

    public AbstractDomainList(DNSService dns, EnvDetector envDetector) {
        this.dns = dns;
        this.envDetector = envDetector;
    }

    public AbstractDomainList(DNSService dns) {
        this(dns, new EnvDetector());
    }

    @Override
    public void configure(HierarchicalConfiguration config) throws ConfigurationException {
        DomainListConfiguration domainListConfiguration = DomainListConfiguration.from(config);

        configure(domainListConfiguration);
    }

    public void configure(DomainListConfiguration domainListConfiguration) throws ConfigurationException {
        setAutoDetect(domainListConfiguration.isAutoDetect());
        setAutoDetectIP(domainListConfiguration.isAutoDetectIp());

        configureDefaultDomain(domainListConfiguration.getDefaultDomain());

        addEnvDomain();
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


    private void addEnvDomain() {
        String envDomain = envDetector.getEnv(ENV_DOMAIN);
        if (!Strings.isNullOrEmpty(envDomain)) {
            try {
                LOGGER.info("Adding environment defined domain {}", envDomain);
                addDomain(Domain.of(envDomain));
            } catch (DomainListException e) {
                throw new RuntimeException(e);
            }
        }
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
        return autoDetect && Domain.LOCALHOST.equals(defaultDomain);
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
        return internalAnswer || getDomains().contains(domain);
    }

    @Override
    public ImmutableList<Domain> getDomains() throws DomainListException {
        List<Domain> domains = getDomainListInternal();
        ImmutableList<Domain> detectedDomains = detectDomains();
        // Guava does not support concatenating ImmutableLists at this time:
        // https://stackoverflow.com/questions/37919648/concatenating-immutablelists
        // A work-around is to use Iterables.concat() until something like
        // https://github.com/google/guava/issues/1029 is implemented.
        Iterable<Domain> domainsWithoutIp = Iterables.concat(domains, detectedDomains);
        ImmutableList<Domain> detectedIps = detectIps(domainsWithoutIp);
        ImmutableList<Domain> allDomains = ImmutableList.copyOf(Iterables.concat(domainsWithoutIp, detectedIps));

        if (LOGGER.isDebugEnabled()) {
            for (Domain domain : allDomains) {
                LOGGER.debug("Handling mail for: " + domain.name());
            }
        }

        return allDomains;
    }

    private ImmutableList<Domain> detectIps(Iterable<Domain> domains) {
        if (autoDetectIP) {
            return getDomainsIpStream(domains, dns, LOGGER)
                .collect(Guavate.toImmutableList());
        }
        return ImmutableList.of();
    }

    private ImmutableList<Domain> detectDomains() {
        if (autoDetect) {
            String hostName;
            try {
                hostName = dns.getHostName(dns.getLocalHost());
            } catch (UnknownHostException ue) {
                hostName = "localhost";
            }

            LOGGER.info("Local host is: {}", hostName);
            if (hostName != null && !hostName.equals("localhost")) {
                return ImmutableList.of(Domain.of(hostName));
            }
        }
        return ImmutableList.of();
    }

    /**
     * Return a stream of all IP addresses of the given domains.
     * 
     * @param domains
     *            Iterable of domains
     * @return Stream of ipaddress for domains
     */
    private static Stream<Domain> getDomainsIpStream(Iterable<Domain> domains, DNSService dns, Logger log) {
        return Guavate.stream(domains)
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

    /**
     * Set to true to autodetect the hostname of the host on which james is
     * running, and add this to the domain service Default is true
     * 
     * @param autoDetect
     *            set to <code>false</code> for disable
     */
    public synchronized void setAutoDetect(boolean autoDetect) {
        LOGGER.info("Set autodetect to: {}", autoDetect);
        this.autoDetect = autoDetect;
    }

    /**
     * Set to true to lookup the ipaddresses for each given domain and add these
     * to the domain service Default is true
     * 
     * @param autoDetectIP
     *            set to <code>false</code> for disable
     */
    public synchronized void setAutoDetectIP(boolean autoDetectIP) {
        LOGGER.info("Set autodetectIP to: {}", autoDetectIP);
        this.autoDetectIP = autoDetectIP;
    }

    /**
     * Return domainList
     * 
     * @return List
     */
    protected abstract List<Domain> getDomainListInternal() throws DomainListException;

    protected abstract boolean containsDomainInternal(Domain domain) throws DomainListException;

}
