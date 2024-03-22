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
package org.apache.james.dnsservice.dnsjava;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.api.DNSServiceMBean;
import org.apache.james.dnsservice.api.TemporaryResolutionException;
import org.apache.james.lifecycle.api.Configurable;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.api.TimeMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.Cache;
import org.xbill.DNS.Credibility;
import org.xbill.DNS.DClass;
import org.xbill.DNS.ExtendedResolver;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.MXRecord;
import org.xbill.DNS.Name;
import org.xbill.DNS.PTRRecord;
import org.xbill.DNS.Record;
import org.xbill.DNS.Resolver;
import org.xbill.DNS.ResolverConfig;
import org.xbill.DNS.ReverseMap;
import org.xbill.DNS.TXTRecord;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;

import com.google.common.collect.ImmutableList;

/**
 * Provides DNS client functionality to services running inside James
 */
public class DNSJavaService implements DNSService, DNSServiceMBean, Configurable {
    private static final Logger LOGGER = LoggerFactory.getLogger(DNSJavaService.class);

    private static final int CACHE_TTL_DISABLE = -1;

    /**
     * A resolver instance used to retrieve DNS records. This is a reference to
     * a third party library object.
     */
    protected Resolver resolver;

    /**
     * A TTL cache of results received from the DNS server. This is a reference
     * to a third party library object.
     */
    protected Cache cache;

    /**
     * Maximum number of RR to cache.
     */
    private int maxCacheSize = 50000;

    private int negativeCacheTTL;

    /**
     * Whether the DNS response is required to be authoritative
     */
    private int dnsCredibility;

    /**
     * The DNS servers to be used by this service
     */
    private final List<String> dnsServers = new ArrayList<>();

    private final MetricFactory metricFactory;

    /**
     * The search paths to be used
     */
    private Name[] searchPaths = null;

    /**
     * The MX Comparator used in the MX sort.
     *
     * RFC 2821 section 5 requires that we sort the MX records by their
     * preference.
     */
    private final Comparator<MXRecord> mxComparator = Comparator.comparing(MXRecord::getPriority);

    /**
     * If true register this service as the default resolver/cache for DNSJava
     * static calls
     */
    private boolean setAsDNSJavaDefault;

    private String localHostName;

    private String localCanonicalHostName;

    private String localAddress;

    @Inject
    public DNSJavaService(MetricFactory metricFactory) {
        this.metricFactory = metricFactory;
    }

    @Override
    public void configure(HierarchicalConfiguration<ImmutableNode> configuration) throws ConfigurationException {
        boolean verbose = configuration.getBoolean("verbose", false);
        if (verbose) {
            System.setProperty("dnsjava.options", "verbose,verbosemsg,verbosecache");
        }

        boolean autodiscover = configuration.getBoolean("autodiscover", true);

        List<Name> sPaths = new ArrayList<>();
        if (autodiscover) {
            LOGGER.info("Autodiscovery is enabled - trying to discover your system's DNS Servers");
            List<InetSocketAddress> serversArray = ResolverConfig.getCurrentConfig().servers();
            if (serversArray != null) {
                for (InetSocketAddress aServersArray : serversArray) {
                    dnsServers.add(aServersArray.getHostString());
                    LOGGER.info("Adding autodiscovered server {}", aServersArray);
                }
            }
            List<Name> systemSearchPath = ResolverConfig.getCurrentConfig().searchPath();
            if (systemSearchPath != null && systemSearchPath.size() > 0) {
                sPaths.addAll(systemSearchPath);
            }
            if (LOGGER.isInfoEnabled()) {
                for (Name searchPath : sPaths) {
                    LOGGER.info("Adding autodiscovered search path " + searchPath);
                }
            }
        }

        // singleIPPerMX = configuration.getBoolean( "singleIPperMX", false );

        setAsDNSJavaDefault = configuration.getBoolean("setAsDNSJavaDefault", true);

        // Get the DNS servers that this service will use for lookups
        Collections.addAll(dnsServers, configuration.getStringArray("servers.server"));

        // Get the DNS servers that this service will use for lookups
        for (String aSearchPathsConfiguration : configuration.getStringArray("searchpaths.searchpath")) {
            try {
                sPaths.add(Name.fromString(aSearchPathsConfiguration));
            } catch (TextParseException e) {
                throw new ConfigurationException("Unable to parse searchpath host: " + aSearchPathsConfiguration, e);
            }
        }

        searchPaths = sPaths.toArray(Name[]::new);

        if (dnsServers.isEmpty()) {
            LOGGER.info("No DNS servers have been specified or found by autodiscovery - adding 127.0.0.1");
            dnsServers.add("127.0.0.1");
        }

        boolean authoritative = configuration.getBoolean("authoritative", false);
        // TODO: Check to see if the credibility field is being used correctly.
        // From the
        // docs I don't think so
        dnsCredibility = authoritative ? Credibility.AUTH_ANSWER : Credibility.NONAUTH_ANSWER;

        maxCacheSize = configuration.getInt("maxcachesize", maxCacheSize);

        negativeCacheTTL = configuration.getInt("negativeCacheTTL", CACHE_TTL_DISABLE);
    }

    @PostConstruct
    public void init() throws Exception {
        LOGGER.debug("DNSService init...");

        // If no DNS servers were configured, default to local host
        if (dnsServers.isEmpty()) {
            try {
                dnsServers.add(InetAddress.getLocalHost().getHostName());
            } catch (UnknownHostException ue) {
                dnsServers.add("127.0.0.1");
            }
        }

        // Create the extended resolver...
        final String[] serversArray = dnsServers.toArray(String[]::new);

        if (LOGGER.isInfoEnabled()) {
            for (String aServersArray : serversArray) {
                LOGGER.info("DNS Server is: " + aServersArray);
            }
        }

        try {
            resolver = new ExtendedResolver(serversArray);
        } catch (UnknownHostException uhe) {
            LOGGER.error("DNS service could not be initialized.  The DNS servers specified are not recognized hosts.", uhe);
            throw uhe;
        }

        cache = new Cache(DClass.IN);
        cache.setMaxEntries(maxCacheSize);
        cache.setMaxNCache(negativeCacheTTL);

        if (setAsDNSJavaDefault) {
            Lookup.setDefaultResolver(resolver);
            Lookup.setDefaultCache(cache, DClass.IN);
            Lookup.setDefaultSearchPath(searchPaths);
            LOGGER.info("Registered cache, resolver and search paths as DNSJava defaults");
        }

        // Cache the local hostname and local address. This is needed because
        // the following issues:
        // JAMES-787
        // JAMES-302
        InetAddress addr = getLocalHost();
        localCanonicalHostName = addr.getCanonicalHostName();
        localHostName = addr.getHostName();
        localAddress = addr.getHostAddress();

        LOGGER.debug("DNSService ...init end");
    }

    /**
     * Return the list of DNS servers in use by this service
     *
     * @return an array of DNS server names
     */
    @Override
    public String[] getDNSServers() {
        return dnsServers.toArray(String[]::new);
    }

    /**
     * Return the list of DNS servers in use by this service
     *
     * @return an array of DNS server names
     */
    public Name[] getSearchPaths() {
        return searchPaths;
    }

    /**
     * Return a prioritized unmodifiable list of MX records obtained from the
     * server.
     *
     * @param hostname domain name to look up
     * @return a list of MX records corresponding to this mail domain
     * @throws TemporaryResolutionException get thrown on temporary problems
     */
    private List<String> findMXRecordsRaw(String hostname) throws TemporaryResolutionException {
        Record[] answers = lookup(hostname, Type.MX, "MX");
        List<String> servers = new ArrayList<>();
        if (answers == null) {
            return servers;
        }

        MXRecord[] mxAnswers = new MXRecord[answers.length];

        for (int i = 0; i < answers.length; i++) {
            mxAnswers[i] = (MXRecord) answers[i];
        }
        // just sort for now.. This will ensure that mx records with same prio
        // are in sequence
        Arrays.sort(mxAnswers, mxComparator);

        // now add the mx records to the right list and take care of shuffle
        // mx records with the same priority
        int currentPrio = -1;
        List<String> samePrio = new ArrayList<>();
        for (int i = 0; i < mxAnswers.length; i++) {
            boolean same = false;
            boolean lastItem = i + 1 == mxAnswers.length;
            MXRecord mx = mxAnswers[i];
            if (i == 0) {
                currentPrio = mx.getPriority();
            } else {
                same = currentPrio == mx.getPriority();
            }

            String mxRecord = mx.getTarget().toString();
            if (same) {
                samePrio.add(mxRecord);
            } else {
                // shuffle entries with same prio
                // JAMES-913
                Collections.shuffle(samePrio);
                servers.addAll(samePrio);

                samePrio.clear();
                samePrio.add(mxRecord);

            }

            if (lastItem) {
                // shuffle entries with same prio
                // JAMES-913
                Collections.shuffle(samePrio);
                servers.addAll(samePrio);
            }
            LOGGER.debug("Found MX record {}", mxRecord);
        }
        return servers;
    }

    @Override
    public Collection<String> findMXRecords(String hostname) throws TemporaryResolutionException {
        TimeMetric timeMetric = metricFactory.timer("findMXRecords");
        List<String> servers = new ArrayList<>();
        try {
            servers = findMXRecordsRaw(hostname);
            return Collections.unmodifiableCollection(servers);
        } finally {
            // If we found no results, we'll add the original domain name if
            // it's a valid DNS entry
            if (servers.size() == 0) {
                LOGGER.info("Couldn't resolve MX records for domain {}.", hostname);
                try {
                    getByName(hostname);
                    servers.add(hostname);
                } catch (UnknownHostException uhe) {
                    // The original domain name is not a valid host,
                    // so we can't add it to the server list. In this
                    // case we return an empty list of servers
                    LOGGER.error("Couldn't resolve IP address for host {}.", hostname, uhe);
                }
            }
            timeMetric.stopAndPublish();
        }
    }

    /**
     * Looks up DNS records of the specified type for the specified name.
     * <p/>
     * This method is a public wrapper for the private implementation method
     *
     * @param namestr  the name of the host to be looked up
     * @param type     the type of record desired
     * @param typeDesc the description of the record type, for debugging purpose
     */
    protected Record[] lookup(String namestr, int type, String typeDesc) throws TemporaryResolutionException {
        // Name name = null;
        try {
            // name = Name.fromString(namestr, Name.root);
            Lookup l = new Lookup(namestr, type);

            l.setCache(cache);
            l.setResolver(resolver);
            l.setCredibility(dnsCredibility);
            l.setSearchPath(searchPaths);
            Record[] r = l.run();

            try {
                if (l.getResult() == Lookup.TRY_AGAIN) {
                    throw new TemporaryResolutionException("DNSService is temporary not reachable");
                } else {
                    return r;
                }
            } catch (IllegalStateException ise) {
                // This is okay, because it mimics the original behaviour
                // TODO find out if it's a bug in DNSJava
                LOGGER.warn("Error determining result ", ise);
                throw new TemporaryResolutionException("DNSService is temporary not reachable");
            }

            // return rawDNSLookup(name, false, type, typeDesc);
        } catch (TextParseException tpe) {
            // TODO: Figure out how to handle this correctly.
            LOGGER.error("Couldn't parse name {}", namestr, tpe);
            return null;
        }
    }

    protected Record[] lookupNoException(String namestr, int type, String typeDesc) {
        try {
            return lookup(namestr, type, typeDesc);
        } catch (TemporaryResolutionException e) {
            return null;
        }
    }



    /*
     * java.net.InetAddress.get[All]ByName(String) allows an IP literal to be
     * passed, and will recognize it even with a trailing '.'. However,
     * org.xbill.DNS.Address does not recognize an IP literal with a trailing
     * '.' character. The problem is that when we lookup an MX record for some
     * domains, we may find an IP address, which will have had the trailing '.'
     * appended by the time we get it back from dnsjava. An MX record is not
     * allowed to have an IP address as the right-hand-side, but there are still
     * plenty of such records on the Internet. Since java.net.InetAddress can
     * handle them, for the time being we've decided to support them.
     * 
     * These methods are NOT intended for use outside of James, and are NOT
     * declared by the org.apache.james.services.DNSServer. This is currently a
     * stopgap measure to be revisited for the next release.
     */

    private static String allowIPLiteral(String host) {
        if ((host.charAt(host.length() - 1) == '.')) {
            String possibleIpLiteral = host.substring(0, host.length() - 1);
            if (org.xbill.DNS.Address.isDottedQuad(possibleIpLiteral)) {
                host = possibleIpLiteral;
            }
        }
        return host;
    }

    @Override
    public InetAddress getByName(String host) throws UnknownHostException {
        TimeMetric timeMetric = metricFactory.timer("getByName");
        String name = allowIPLiteral(host);

        try {
            // Check if its local
            if (name.equalsIgnoreCase(localHostName) || name.equalsIgnoreCase(localCanonicalHostName) || name.equals(localAddress)) {
                return getLocalHost();
            }

            return org.xbill.DNS.Address.getByAddress(name);
        } catch (UnknownHostException e) {
            Record[] records = lookupNoException(name, Type.A, "A");

            if (records != null && records.length >= 1) {
                ARecord a = (ARecord) records[0];
                return InetAddress.getByAddress(name, a.getAddress().getAddress());
            } else {
                throw e;
            }
        } finally {
            timeMetric.stopAndPublish();
        }
    }

    @Override
    public Collection<InetAddress> getAllByName(String host) throws UnknownHostException {
        TimeMetric timeMetric = metricFactory.timer("getAllByName");
        String name = allowIPLiteral(host);
        try {
            // Check if its local
            if (name.equalsIgnoreCase(localHostName) || name.equalsIgnoreCase(localCanonicalHostName) || name.equals(localAddress)) {
                return ImmutableList.of(getLocalHost());
            }

            InetAddress addr = org.xbill.DNS.Address.getByAddress(name);
            return ImmutableList.of(addr);
        } catch (UnknownHostException e) {
            Record[] records = lookupNoException(name, Type.A, "A");

            if (records != null && records.length >= 1) {
                InetAddress[] addrs = new InetAddress[records.length];
                for (int i = 0; i < records.length; i++) {
                    ARecord a = (ARecord) records[i];
                    addrs[i] = InetAddress.getByAddress(name, a.getAddress().getAddress());
                }
                return ImmutableList.copyOf(addrs);
            } else {
                throw e;
            }
        } finally {
            timeMetric.stopAndPublish();
        }
    }

    @Override
    public Collection<String> findTXTRecords(String hostname) {
        TimeMetric timeMetric = metricFactory.timer("findTXTRecords");
        List<String> txtR = new ArrayList<>();
        Record[] records = lookupNoException(hostname, Type.TXT, "TXT");

        try {
            if (records != null) {
                for (Record record : records) {
                    TXTRecord txt = (TXTRecord) record;
                    txtR.add(txt.rdataToString());
                }

            }
            return txtR;
        } finally {
            timeMetric.stopAndPublish();
        }
    }

    @Override
    public String getHostName(InetAddress addr) {
        TimeMetric timeMetric = metricFactory.timer("getHostName");
        String result;
        Name name = ReverseMap.fromAddress(addr);
        Record[] records = lookupNoException(name.toString(), Type.PTR, "PTR");

        try {
            if (records == null) {
                result = addr.getHostAddress();
            } else {
                PTRRecord ptr = (PTRRecord) records[0];
                result = ptr.getTarget().toString();
            }
            return result;
        } finally {
            timeMetric.stopAndPublish();
        }
    }

    @Override
    public InetAddress getLocalHost() throws UnknownHostException {
        return InetAddress.getLocalHost();
    }

    @Override
    public int getMaximumCacheSize() {
        return maxCacheSize;
    }

    @Override
    public int getCurrentCacheSize() {
        return cache.getSize();
    }

    @Override
    public void clearCache() {
        cache.clearCache();
    }

}
