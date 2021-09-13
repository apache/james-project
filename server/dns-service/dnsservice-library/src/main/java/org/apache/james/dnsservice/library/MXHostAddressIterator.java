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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.james.dnsservice.api.DNSService;
import org.apache.mailet.HostAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 *
 *
 */
@SuppressWarnings("deprecation")
public class MXHostAddressIterator implements Iterator<HostAddress> {
    private static final Logger LOGGER = LoggerFactory.getLogger(MXHostAddressIterator.class);
    public static final boolean SMTP_ONLY = false;

    private final Iterator<HostAddress> addresses;

    public MXHostAddressIterator(Iterator<String> hosts, DNSService dns, boolean useSingleIP) {
        this(hosts, dns, useSingleIP, SMTP_ONLY);
    }

    public MXHostAddressIterator(Iterator<String> hosts, DNSService dns, boolean useSingleIP, boolean smtps) {
        checkNotNull(hosts, "Hosts is null");
        checkNotNull(dns, "Dns is null");
        final List<HostAddress> hAddresses = Lists.newArrayList();

        while (hosts.hasNext()) {
            String nextHostname = hosts.next();

            Map.Entry<String, Optional<String>> hostAndPort = extractHostAndPort(nextHostname);

            try {
                final Collection<InetAddress> addrs;
                if (useSingleIP) {
                    addrs = ImmutableList.of(dns.getByName(hostAndPort.getKey()));
                } else {
                    addrs = dns.getAllByName(hostAndPort.getKey());
                }
                for (InetAddress addr : addrs) {
                    if (smtps) {
                        hAddresses.add(new HostAddress(hostAndPort.getKey(),
                            "smtps://" + addr.getHostAddress() + ":" + hostAndPort.getValue().orElse("465")));
                    }
                    hAddresses.add(new HostAddress(hostAndPort.getKey(),
                        "smtp://" + addr.getHostAddress() + ":" + hostAndPort.getValue().orElse("25")));
                }
            } catch (UnknownHostException uhe) {
                // this should never happen, since we just got
                // this host from mxHosts, which should have
                // already done this check.
                String logBuffer = "Couldn't resolve IP address for discovered host " + hostAndPort.getKey() + ".";
                LOGGER.error(logBuffer);
            }
        }
        addresses = hAddresses.iterator();
    }

    private static ImmutableMap.Entry<String, Optional<String>> extractHostAndPort(String nextHostname) {
        final String hostname;
        final Optional<String> port;

        int idx = nextHostname.indexOf(':');
        if (idx > 0) {
            port = Optional.of(nextHostname.substring(idx + 1));
            hostname = nextHostname.substring(0, idx);
        } else {
            hostname = nextHostname;
            port = Optional.empty();
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

    @Override
    public void remove() {
        throw new UnsupportedOperationException("remove not supported by this iterator");
    }
}
