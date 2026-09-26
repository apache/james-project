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
import java.security.Security;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

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
import org.xbill.DNS.SOARecord;
import org.xbill.DNS.TXTRecord;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.google.common.collect.ImmutableList;

/**
 * Provides DNS client functionality to services running inside James
 */
public class DNSJavaService implements DNSService, DNSServiceMBean, Configurable {
    private static final Logger LOGGER = LoggerFactory.getLogger(DNSJavaService.class);

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

    public static final int ABSOLUTE_MAX_TTL = 7 * 86400; // 7 days safety cap
    public static final int ABSOLUTE_MIN_TTL = 60; // 60 seconds minimum protective safety cap
    public static final int DEFAULT_CACHE_FALLBACK_TTL = 300; // 5 minutes, used when inheritTTL=false or no TTL available
    public static final int DEFAULT_CACHE_MIN_TTL = 60;
    public static final int DEFAULT_CACHE_MAX_TTL = 86400; // 1 day
    public static final int DEFAULT_NEGATIVE_CACHE_FALLBACK_TTL = 60; // used when inheritNegativeTTL=false or no SOA TTL available
    public static final int DEFAULT_NEGATIVE_CACHE_MIN_TTL = 60;
    public static final int DEFAULT_NEGATIVE_CACHE_MAX_TTL = 3600; // 1 hour safety cap for negative cache

    private boolean inheritTTL = true;
    private int cacheFallbackTTL = DEFAULT_CACHE_FALLBACK_TTL;
    private int cacheMinTTL = DEFAULT_CACHE_MIN_TTL;
    private int cacheMaxTTL = DEFAULT_CACHE_MAX_TTL;

    private boolean inheritNegativeTTL = true;
    private int negativeCacheFallbackTTL = DEFAULT_NEGATIVE_CACHE_FALLBACK_TTL;
    private int negativeCacheMinTTL = DEFAULT_NEGATIVE_CACHE_MIN_TTL;
    private int negativeCacheMaxTTL = DEFAULT_NEGATIVE_CACHE_MAX_TTL;

    /**
     * Whether the DNS response is required to be authoritative
     */
    private int dnsCredibility;

    private enum DnsRecordType { MX, PTR, A, TXT }

    private record DnsKey(DnsRecordType type, Object target) {}

    private record DnsValue<T>(T value, long ttlSeconds) {}

    private record MxHost(String name, int priority) {}

    protected com.github.benmanes.caffeine.cache.Cache<DnsKey, DnsValue<?>> caffeineCache;

    private static int sanitizeBoundedTtl(int value, int defaultVal, int minAllowed, int maxAllowed) {
        if (value < minAllowed) {
            LOGGER.warn("Configured TTL {} is below minimum allowed {}. Falling back to {}.", value, minAllowed, defaultVal);
            return defaultVal;
        }
        if (value > maxAllowed) {
            LOGGER.warn("Configured TTL {} exceeds maximum allowed {}. Clamping to {}.", value, maxAllowed, maxAllowed);
            return maxAllowed;
        }
        return value;
    }

    private static int resolveJvmSecurityTtl(String secProp, String sysProp, int fallback) {
        try {
            String val = Security.getProperty(secProp);
            if (val != null && !val.trim().isEmpty()) {
                int parsed = Integer.parseInt(val.trim());
                if (parsed >= 0) {
                    return parsed;
                }
            }
        } catch (Exception e) {
            // ignore
        }
        try {
            String sysVal = System.getProperty(sysProp);
            if (sysVal != null && !sysVal.trim().isEmpty()) {
                int parsed = Integer.parseInt(sysVal.trim());
                if (parsed >= 0) {
                    return parsed;
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return fallback;
    }

    private static long ttlNanos(DnsValue<?> value) {
        return TimeUnit.SECONDS.toNanos(Math.max(1, value.ttlSeconds()));
    }

    private long clampTtl(long rawTtl) {
        long effectiveMin = Math.max((long) ABSOLUTE_MIN_TTL, (long) cacheMinTTL);
        return Math.max(effectiveMin, Math.min(rawTtl, (long) cacheMaxTTL));
    }

    private long clampNegativeTtl(long rawTtl) {
        long effectiveMin = Math.max((long) ABSOLUTE_MIN_TTL, (long) negativeCacheMinTTL);
        return Math.max(effectiveMin, Math.min(rawTtl, (long) negativeCacheMaxTTL));
    }

    private long computeRecordsTtl(Record[] records, String hostname) {
        if (records == null || records.length == 0) {
            if (inheritNegativeTTL && hostname != null && !hostname.isEmpty()) {
                Record[] soa = lookupNoException(hostname, Type.SOA);
                if (soa != null && soa.length > 0 && soa[0] instanceof SOARecord soaRecord) {
                    long negativeTtl = Math.min(soaRecord.getTTL(), soaRecord.getMinimum());
                    return clampNegativeTtl(negativeTtl);
                }
            }
            return clampNegativeTtl(negativeCacheFallbackTTL);
        }
        if (!inheritTTL) {
            return clampTtl(cacheFallbackTTL);
        }
        long minTtl = Long.MAX_VALUE;
        for (Record record : records) {
            if (record != null) {
                minTtl = Math.min(minTtl, record.getTTL());
            }
        }
        return minTtl == Long.MAX_VALUE ? clampNegativeTtl(negativeCacheFallbackTTL) : clampTtl(minTtl);
    }

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
            if (systemSearchPath != null && !systemSearchPath.isEmpty()) {
                sPaths.addAll(systemSearchPath);
            }
            if (LOGGER.isInfoEnabled()) {
                for (Name searchPath : sPaths) {
                    LOGGER.info("Adding autodiscovered search path {}", searchPath);
                }
            }
        }

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

        inheritTTL = configuration.getBoolean("inheritTTL", true);
        inheritNegativeTTL = configuration.getBoolean("inheritNegativeTTL", true);

        int jvmPosTtl = resolveJvmSecurityTtl("networkaddress.cache.ttl", "sun.net.inetaddr.ttl", DEFAULT_CACHE_MAX_TTL);
        int jvmNegTtl = resolveJvmSecurityTtl("networkaddress.cache.negative.ttl", "sun.net.inetaddr.negative.ttl", DEFAULT_NEGATIVE_CACHE_FALLBACK_TTL);

        int rawFallbackTtl = configuration.getInt("cacheFallbackTTL", DEFAULT_CACHE_FALLBACK_TTL);
        cacheFallbackTTL = sanitizeBoundedTtl(rawFallbackTtl, DEFAULT_CACHE_FALLBACK_TTL, ABSOLUTE_MIN_TTL, ABSOLUTE_MAX_TTL);

        int rawMinTtl = configuration.getInt("cacheMinTTL", DEFAULT_CACHE_MIN_TTL);
        cacheMinTTL = sanitizeBoundedTtl(rawMinTtl, DEFAULT_CACHE_MIN_TTL, ABSOLUTE_MIN_TTL, ABSOLUTE_MAX_TTL);

        int rawMaxTtl = configuration.getInt("cacheMaxTTL", jvmPosTtl);
        cacheMaxTTL = sanitizeBoundedTtl(rawMaxTtl, jvmPosTtl, cacheMinTTL, ABSOLUTE_MAX_TTL);

        int rawNegFallbackTtl = configuration.getInt("negativeCacheFallbackTTL", jvmNegTtl);
        negativeCacheFallbackTTL = sanitizeBoundedTtl(rawNegFallbackTtl, jvmNegTtl, ABSOLUTE_MIN_TTL, DEFAULT_NEGATIVE_CACHE_MAX_TTL);

        int rawNegMinTtl = configuration.getInt("negativeCacheMinTTL", DEFAULT_NEGATIVE_CACHE_MIN_TTL);
        negativeCacheMinTTL = sanitizeBoundedTtl(rawNegMinTtl, DEFAULT_NEGATIVE_CACHE_MIN_TTL, ABSOLUTE_MIN_TTL, DEFAULT_NEGATIVE_CACHE_MAX_TTL);

        int rawNegMaxTtl = configuration.getInt("negativeCacheMaxTTL", DEFAULT_NEGATIVE_CACHE_MAX_TTL);
        negativeCacheMaxTTL = sanitizeBoundedTtl(rawNegMaxTtl, DEFAULT_NEGATIVE_CACHE_MAX_TTL, negativeCacheMinTTL, DEFAULT_NEGATIVE_CACHE_MAX_TTL);
    }

    @PostConstruct
    public void init() throws UnknownHostException {
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
                LOGGER.info("DNS Server is: {}", aServersArray);
            }
        }

        resolver = new ExtendedResolver(serversArray);

        cache = new Cache(DClass.IN);
        cache.setMaxEntries(maxCacheSize);
        cache.setMaxCache(cacheMaxTTL);
        cache.setMaxNCache(negativeCacheFallbackTTL);

        caffeineCache = Caffeine.newBuilder()
            .maximumSize(maxCacheSize)
            .expireAfter(new Expiry<DnsKey, DnsValue<?>>() {
                @Override
                public long expireAfterCreate(DnsKey key, DnsValue<?> value, long currentTime) {
                    return ttlNanos(value);
                }

                @Override
                public long expireAfterUpdate(DnsKey key, DnsValue<?> value, long currentTime, long currentDuration) {
                    return ttlNanos(value);
                }

                @Override
                public long expireAfterRead(DnsKey key, DnsValue<?> value, long currentTime, long currentDuration) {
                    return currentDuration;
                }
            })
            .build();

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
     * Return a prioritized unmodifiable list of MX records obtained from the
     * server.
     *
     * @param hostname domain name to look up
     * @return a list of MX records corresponding to this mail domain
     * @throws TemporaryResolutionException get thrown on temporary problems
     */
    private List<MxHost> findMXRecordsRaw(String hostname) throws TemporaryResolutionException {
        Record[] answers = lookup(hostname, Type.MX);
        List<MxHost> servers = new ArrayList<>();
        if (answers == null) {
            return servers;
        }

        MXRecord[] mxAnswers = new MXRecord[answers.length];
        for (int i = 0; i < answers.length; i++) {
            mxAnswers[i] = (MXRecord) answers[i];
        }
        Arrays.sort(mxAnswers, mxComparator);

        for (MXRecord mx : mxAnswers) {
            servers.add(new MxHost(mx.getTarget().toString(), mx.getPriority()));
        }
        return servers;
    }

    private static List<String> shuffleEqualPriorityMx(List<MxHost> mxHosts) {
        List<String> result = new ArrayList<>(mxHosts.size());
        int currentPrio = -1;
        List<String> samePrio = new ArrayList<>();

        for (int i = 0; i < mxHosts.size(); i++) {
            MxHost host = mxHosts.get(i);
            boolean same = false;
            boolean lastItem = (i + 1 == mxHosts.size());

            if (i == 0) {
                currentPrio = host.priority();
            } else {
                same = (currentPrio == host.priority());
            }

            if (same) {
                samePrio.add(host.name());
            } else {
                if (samePrio.size() > 1) {
                    Collections.shuffle(samePrio);
                }
                result.addAll(samePrio);
                samePrio.clear();
                currentPrio = host.priority();
                samePrio.add(host.name());
            }

            if (lastItem) {
                if (samePrio.size() > 1) {
                    Collections.shuffle(samePrio);
                }
                result.addAll(samePrio);
            }
        }
        return ImmutableList.copyOf(result);
    }

    @Override
    public Collection<String> findMXRecords(String hostname) throws TemporaryResolutionException {
        if (caffeineCache != null) {
            DnsKey key = new DnsKey(DnsRecordType.MX, normalizeKey(hostname));
            DnsValue<?> cached = caffeineCache.getIfPresent(key);
            if (cached != null) {
                @SuppressWarnings("unchecked")
                List<MxHost> cachedHosts = (List<MxHost>) cached.value();
                return shuffleEqualPriorityMx(cachedHosts);
            }
        }

        TimeMetric timeMetric = metricFactory.timer("findMXRecords");
        try {
            List<MxHost> hosts = findMXRecordsRaw(hostname);
            // If we found no results, we'll add the original domain name if it's a valid DNS entry
            if (hosts.isEmpty()) {
                LOGGER.info("Couldn't resolve MX records for domain {}.", hostname);
                try {
                    getByName(hostname);
                    hosts.add(new MxHost(hostname, 0));
                } catch (UnknownHostException uhe) {
                    LOGGER.error("Couldn't resolve IP address for host {}.", hostname, uhe);
                }
            }

            List<MxHost> immutableHosts = ImmutableList.copyOf(hosts);
            if (caffeineCache != null) {
                Record[] records = lookupNoException(hostname, Type.MX);
                long ttl = computeRecordsTtl(records, hostname);
                caffeineCache.put(new DnsKey(DnsRecordType.MX, normalizeKey(hostname)), new DnsValue<>(immutableHosts, ttl));
            }
            return shuffleEqualPriorityMx(immutableHosts);
        } finally {
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
     */
    protected Record[] lookup(String namestr, int type) throws TemporaryResolutionException {
        try {
            Lookup l = new Lookup(namestr, type);

            if (caffeineCache == null) {
                l.setCache(cache);
            }
            l.setResolver(resolver);
            l.setCredibility(dnsCredibility);
            l.setSearchPath(searchPaths);
            Record[] r = l.run();

            if (l.getResult() == Lookup.TRY_AGAIN) {
                throw new TemporaryResolutionException("DNSService is temporary not reachable");
            } else {
                return r;
            }

        } catch (TextParseException tpe) {
            // TODO: Figure out how to handle this correctly.
            LOGGER.error("Couldn't parse name {}", namestr, tpe);
            return null;
        } catch (IllegalStateException ise) {
            // This is okay, because it mimics the original behaviour
            // TODO find out if it's a bug in DNSJava
            throw new TemporaryResolutionException("DNSService is temporary not reachable", ise);
        }
    }

    protected Record[] lookupNoException(String namestr, int type) {
        try {
            return lookup(namestr, type);
        } catch (TemporaryResolutionException e) {
            return null;
        }
    }

    private static String normalizeKey(String host) {
        return host.toLowerCase(Locale.ROOT);
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
        return getAllByName(host).iterator().next();
    }

    @Override
    public Collection<InetAddress> getAllByName(String host) throws UnknownHostException {
        if (caffeineCache != null) {
            DnsKey key = new DnsKey(DnsRecordType.A, normalizeKey(host));
            DnsValue<?> cached = caffeineCache.getIfPresent(key);
            if (cached != null) {
                @SuppressWarnings("unchecked")
                Collection<InetAddress> cachedResult = (Collection<InetAddress>) cached.value();
                return cachedResult;
            }
        }

        TimeMetric timeMetric = metricFactory.timer("getAllByName");
        String name = allowIPLiteral(host);
        try {
            // Check if its local
            if (name.equalsIgnoreCase(localHostName) || name.equalsIgnoreCase(localCanonicalHostName) || name.equals(localAddress)) {
                return ImmutableList.of(getLocalHost());
            }

            // Address.getByAddress parses IP literals (both IPv4 and IPv6). If it succeeds, name is an IP literal.
            return ImmutableList.of(org.xbill.DNS.Address.getByAddress(name));
        } catch (UnknownHostException e) {
            Record[] records = lookupNoException(name, Type.A);

            if (records != null && records.length >= 1) {
                InetAddress[] addrs = new InetAddress[records.length];
                for (int i = 0; i < records.length; i++) {
                    ARecord a = (ARecord) records[i];
                    addrs[i] = InetAddress.getByAddress(name, a.getAddress().getAddress());
                }
                Collection<InetAddress> result = ImmutableList.copyOf(addrs);
                if (caffeineCache != null) {
                    long ttl = computeRecordsTtl(records, host);
                    caffeineCache.put(new DnsKey(DnsRecordType.A, normalizeKey(host)), new DnsValue<>(result, ttl));
                }
                return result;
            } else {
                throw e;
            }
        } finally {
            timeMetric.stopAndPublish();
        }
    }

    @Override
    public Collection<String> findTXTRecords(String hostname) {
        if (caffeineCache != null) {
            DnsKey key = new DnsKey(DnsRecordType.TXT, normalizeKey(hostname));
            DnsValue<?> cached = caffeineCache.getIfPresent(key);
            if (cached != null) {
                @SuppressWarnings("unchecked")
                Collection<String> cachedResult = (Collection<String>) cached.value();
                return cachedResult;
            }
        }

        TimeMetric timeMetric = metricFactory.timer("findTXTRecords");
        List<String> txtR = new ArrayList<>();
        Record[] records = lookupNoException(hostname, Type.TXT);

        try {
            if (records != null) {
                for (Record dnsRecord : records) {
                    TXTRecord txt = (TXTRecord) dnsRecord;
                    txtR.add(txt.rdataToString());
                }

            }
            Collection<String> result = ImmutableList.copyOf(txtR);
            if (caffeineCache != null) {
                long ttl = computeRecordsTtl(records, hostname);
                caffeineCache.put(new DnsKey(DnsRecordType.TXT, normalizeKey(hostname)), new DnsValue<>(result, ttl));
            }
            return result;
        } finally {
            timeMetric.stopAndPublish();
        }
    }

    @Override
    public String getHostName(InetAddress addr) {
        if (caffeineCache != null) {
            DnsKey key = new DnsKey(DnsRecordType.PTR, addr);
            DnsValue<?> cached = caffeineCache.getIfPresent(key);
            if (cached != null) {
                return (String) cached.value();
            }
        }

        TimeMetric timeMetric = metricFactory.timer("getHostName");
        String result;
        Name name = ReverseMap.fromAddress(addr);
        String nameString = name.toString();
        Record[] records = lookupNoException(nameString, Type.PTR);

        try {
            if (records == null) {
                result = addr.getHostAddress();
            } else {
                PTRRecord ptr = (PTRRecord) records[0];
                result = ptr.getTarget().toString();
            }
            if (caffeineCache != null) {
                long ttl = computeRecordsTtl(records, nameString);
                caffeineCache.put(new DnsKey(DnsRecordType.PTR, addr), new DnsValue<>(result, ttl));
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
        if (caffeineCache != null) {
            caffeineCache.invalidateAll();
        }
    }

}
