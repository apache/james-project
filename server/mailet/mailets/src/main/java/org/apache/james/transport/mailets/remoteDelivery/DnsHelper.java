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

package org.apache.james.transport.mailets.remoteDelivery;

import java.util.Collection;
import java.util.Iterator;

import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.api.TemporaryResolutionException;
import org.apache.james.dnsservice.library.MXHostAddressIterator;
import org.apache.mailet.HostAddress;
import org.slf4j.Logger;

@SuppressWarnings("deprecation")
public class DnsHelper {

    private final DNSService dnsServer;
    private final RemoteDeliveryConfiguration configuration;
    private final Logger logger;

    public DnsHelper(DNSService dnsServer, RemoteDeliveryConfiguration configuration, Logger logger) {
        this.dnsServer = dnsServer;
        this.configuration = configuration;
        this.logger = logger;
    }

    public Iterator<HostAddress> retrieveHostAddressIterator(String host) throws TemporaryResolutionException {
        if (configuration.getGatewayServer().isEmpty()) {
            return new MXHostAddressIterator(dnsServer.findMXRecords(host).iterator(), dnsServer, false, logger);
        } else {
            return getGatewaySMTPHostAddresses(configuration.getGatewayServer());
        }
    }

    /**
     * Returns an Iterator over org.apache.mailet.HostAddress, a specialized
     * subclass of javax.mail.URLName, which provides location information for
     * servers that are specified as mail handlers for the given hostname. If no
     * host is found, the Iterator returned will be empty and the first call to
     * hasNext() will return false. The Iterator is a nested iterator: the outer
     * iteration is over each gateway, and the inner iteration is over
     * potentially multiple A records for each gateway.
     *
     * @param gatewayServers - Collection of host[:port] Strings
     * @return an Iterator over HostAddress instances, sorted by priority
     * @since v2.2.0a16-unstable
     */
    private Iterator<HostAddress> getGatewaySMTPHostAddresses(Collection<String> gatewayServers) {
        return new MXHostAddressIterator(gatewayServers.iterator(), dnsServer, false, logger);
    }

}
