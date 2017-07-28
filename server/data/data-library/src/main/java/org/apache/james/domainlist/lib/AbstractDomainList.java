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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.lifecycle.api.Configurable;
import org.apache.james.lifecycle.api.LogEnabled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

/**
 * All implementations of the DomainList interface should extends this abstract
 * class
 */
public abstract class AbstractDomainList implements DomainList, LogEnabled, Configurable {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractDomainList.class);

    protected static final String LOCALHOST = "localhost";

    private DNSService dns;
    private boolean autoDetect = true;
    private boolean autoDetectIP = true;
    private Logger logger;
    private String defaultDomain;

    @Inject
    public void setDNSService(DNSService dns) {
        this.dns = dns;
    }

    public void setLog(Logger logger) {
        this.logger = logger;
    }

    protected Logger getLogger() {
        return logger;
    }

    @Override
    public void configure(HierarchicalConfiguration config) throws ConfigurationException {
        configureDefaultDomain(config);

        setAutoDetect(config.getBoolean("autodetect", true));
        setAutoDetectIP(config.getBoolean("autodetectIP", true));
    }

    @VisibleForTesting void configureDefaultDomain(HierarchicalConfiguration config) throws ConfigurationException {
        
        try {
            setDefaultDomain(config.getString("defaultDomain", LOCALHOST));

            String hostName = InetAddress.getLocalHost().getHostName();
            if (mayChangeDefaultDomain()) {
                setDefaultDomain(hostName);
            }
        } catch (UnknownHostException e) {
            LOGGER.warn("Unable to retrieve hostname.", e);
        } catch (DomainListException e) {
            LOGGER.error("An error occured while creating the default domain", e);
        }
    }

    private boolean mayChangeDefaultDomain() {
        return LOCALHOST.equals(defaultDomain);
    }

    private void setDefaultDomain(String defaultDomain) throws DomainListException {
        if (!containsDomain(defaultDomain)) {
            addDomain(defaultDomain);
        }
        this.defaultDomain = defaultDomain;
    }

    @Override
    public String getDefaultDomain() throws DomainListException {
        if (defaultDomain!= null) {
            return defaultDomain;
        } else {
            throw new DomainListException("Null default domain. Domain list might not be configured yet.");
        }
    }

    @Override
    public List<String> getDomains() throws DomainListException {
        List<String> domains = getDomainListInternal();
        if (domains != null) {

            // create mutable copy, some subclasses return ImmutableList
            ArrayList<String> mutableDomains = new ArrayList<String>(domains);

            String hostName;
            try {
                hostName = getDNSServer().getHostName(getDNSServer().getLocalHost());
            } catch (UnknownHostException ue) {
                hostName = "localhost";
            }

            getLogger().info("Local host is: " + hostName);

            if (autoDetect && (!hostName.equals("localhost"))) {
                mutableDomains.add(hostName.toLowerCase(Locale.US));
            }

            if (autoDetectIP) {
                mutableDomains.addAll(getDomainsIP(mutableDomains, dns, getLogger()));
            }

            if (getLogger().isInfoEnabled()) {
                for (String domain : mutableDomains) {
                    getLogger().debug("Handling mail for: " + domain);
                }
            }

            return ImmutableList.copyOf(mutableDomains);
        }
        return ImmutableList.of();// empty list
    }

    /**
     * Return a List which holds all ipAddress of the domains in the given List
     * 
     * @param domains
     *            List of domains
     * @return domainIP List of ipaddress for domains
     */
    private static List<String> getDomainsIP(List<String> domains, DNSService dns, Logger log) {
        List<String> domainIP = new ArrayList<String>();
        if (domains.size() > 0) {
            for (String domain : domains) {
                List<String> domList = getDomainIP(domain, dns, log);

                for (String aDomList : domList) {
                    if (!domainIP.contains(aDomList)) {
                        domainIP.add(aDomList);
                    }
                }
            }
        }
        return domainIP;
    }

    /**
     * @see #getDomainsIP(List, DNSService, Logger)
     */
    private static List<String> getDomainIP(String domain, DNSService dns, Logger log) {
        List<String> domainIP = new ArrayList<String>();
        try {
            Collection<InetAddress> addrs = dns.getAllByName(domain);
            for (InetAddress addr : addrs) {
                String ip = addr.getHostAddress();
                if (!domainIP.contains(ip)) {
                    domainIP.add(ip);
                }
            }
        } catch (UnknownHostException e) {
            log.error("Cannot get IP address(es) for " + domain);
        }
        return domainIP;
    }

    /**
     * Set to true to autodetect the hostname of the host on which james is
     * running, and add this to the domain service Default is true
     * 
     * @param autoDetect
     *            set to <code>false</code> for disable
     */
    public synchronized void setAutoDetect(boolean autoDetect) {
        getLogger().info("Set autodetect to: " + autoDetect);
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
        getLogger().info("Set autodetectIP to: " + autoDetectIP);
        this.autoDetectIP = autoDetectIP;
    }

    /**
     * Return dnsServer
     * 
     * @return dns
     */
    protected DNSService getDNSServer() {
        return dns;
    }

    /**
     * Return domainList
     * 
     * @return List
     */
    protected abstract List<String> getDomainListInternal() throws DomainListException;

}
