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
package org.apache.james.dnsservice.library;

import static com.google.common.base.Preconditions.checkNotNull;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.mailet.HostAddress;
import org.slf4j.Logger;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 *
 *
 */
public class MXHostAddressIterator implements Iterator<HostAddress> {

    private final Iterator<HostAddress> addresses;
    private final Iterator<String> hosts;
    private final DNSService dns;
    private final boolean useSingleIP;
    private final Logger logger;
    private final int defaultPort;

    public MXHostAddressIterator(Iterator<String> hosts, DNSService dns, boolean useSingleIP, Logger logger) {
        this(hosts, 25, dns, useSingleIP, logger);
    }

    public MXHostAddressIterator(Iterator<String> hosts, int defaultPort, DNSService dns, boolean useSingleIP, Logger logger) {
        this.hosts = checkNotNull(hosts, "Hosts is null");
        this.dns = checkNotNull(dns, "Dns is null");
        this.useSingleIP = useSingleIP;
        this.logger = logger;
        this.defaultPort = defaultPort;
        final List<HostAddress> hAddresses = Lists.newArrayList();

        while (hosts.hasNext()) {
            String nextHostname = hosts.next();
            Map.Entry<String, String> hostAndPort = extractHostAndPort(nextHostname, defaultPort);

            try {
                final InetAddress[] addrs;
                if (useSingleIP) {
                    addrs = new InetAddress[]{dns.getByName(hostAndPort.getKey())};
                } else {
                    addrs = dns.getAllByName(hostAndPort.getKey());
                }
                for (InetAddress addr : addrs) {
                    hAddresses.add(new HostAddress(hostAndPort.getKey(),
                            "smtp://" + addr.getHostAddress() + ":" + hostAndPort.getValue()));
                }
            } catch (UnknownHostException uhe) {
                // this should never happen, since we just got
                // this host from mxHosts, which should have
                // already done this check.
                String logBuffer = "Couldn't resolve IP address for discovered host " + hostAndPort.getKey() + ".";
                logger.error(logBuffer);
            }
        }
        addresses = hAddresses.iterator();
    }

    private static ImmutableMap.Entry<String, String> extractHostAndPort(String nextHostname, int defaultPort) {
        final String hostname;
        final String port;

        int idx = nextHostname.indexOf(':');
        if (idx > 0) {
            port = nextHostname.substring(idx + 1);
            hostname = nextHostname.substring(0, idx);
        } else {
            hostname = nextHostname;
            port = Integer.toString(defaultPort);
        }
        return Maps.immutableEntry(hostname, port);
    }

    @Override
    public boolean hasNext() {
        return addresses.hasNext();
    }

    @Override
    public HostAddress next() {
        return addresses.next();
    }

    /**
     * Not supported.
     */
    @Override
    public void remove() {
        throw new UnsupportedOperationException("remove not supported by this iterator");
    }
}
