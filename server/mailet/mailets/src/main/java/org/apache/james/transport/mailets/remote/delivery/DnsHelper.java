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

package org.apache.james.transport.mailets.remote.delivery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.api.TemporaryResolutionException;
import org.apache.james.dnsservice.library.MXHostAddressIterator;
import org.apache.mailet.HostAddress;

@SuppressWarnings("deprecation")
public class DnsHelper {

    public static final boolean USE_SEVERAL_IP = false;
    private final DNSService dnsServer;
    private final RemoteDeliveryConfiguration configuration;

    public DnsHelper(DNSService dnsServer, RemoteDeliveryConfiguration configuration) {
        this.dnsServer = dnsServer;
        this.configuration = configuration;
    }

    public Iterator<HostAddress> retrieveHostAddressIterator(String host, boolean smtps) throws TemporaryResolutionException {
        if (configuration.getGatewayServer().isEmpty()) {
            return new MXHostAddressIterator(dnsServer.findMXRecords(host).iterator(), dnsServer, USE_SEVERAL_IP, smtps);
        } else if (configuration.isLoadBalancing()) {
            List<String> gatewayList = new ArrayList<>(configuration.getGatewayServer());
            Collections.shuffle(gatewayList);
            return new MXHostAddressIterator(gatewayList.iterator(), dnsServer, USE_SEVERAL_IP, smtps);
        } else {
            return new MXHostAddressIterator(configuration.getGatewayServer().iterator(), dnsServer, USE_SEVERAL_IP, smtps);
        }
    }

}
