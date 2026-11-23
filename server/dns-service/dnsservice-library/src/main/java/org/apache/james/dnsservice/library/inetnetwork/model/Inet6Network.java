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
package org.apache.james.dnsservice.library.inetnetwork.model;

import java.math.BigInteger;
import java.net.Inet6Address;
import java.net.InetAddress;

import org.apache.james.dnsservice.library.inetnetwork.InetNetworkBuilder;

import com.google.common.net.InetAddresses;


public class Inet6Network implements InetNetwork {
    public static final int MAX_PREFIX_LENGTH = 128;

    /**
     * The IP address on which a subnet mask is applied.
     */
    private final InetAddress network;

    /**
     * The subnet mask to apply on the IP address.
     */
    private final Integer netmask;

    /**
     * You need a IP address (InetAddress) and an subnetmask (Integer) to
     * construct an Inet6Network.
     *
     * @param ip      the InetAddress to init the class
     * @param netmask the InetAddress represent the netmask to init the class
     */
    public Inet6Network(InetAddress ip, Integer netmask) {
        if (netmask < 0 || netmask > MAX_PREFIX_LENGTH) {
            throw new IllegalArgumentException("Invalid IPv6 prefix length " + netmask);
        }
        network = maskIP(ip, netmask);
        this.netmask = netmask;
    }

    @Override
    public boolean contains(InetAddress ip) {
        if (!InetNetworkBuilder.isV6(ip.getHostAddress())) {
            return false;
        }
        try {
            return network.equals(maskIP(ip, netmask));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public String toString() {
        return network.getHostAddress() + "/" + netmask;
    }

    @Override
    public int hashCode() {
        return maskIP(network, netmask).hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return (obj instanceof InetNetwork) && ((((Inet6Network) obj).network.equals(network)) && (((Inet6Network) obj).netmask.equals(netmask)));
    }

    /**
     * The network part of the address: its first prefixLength bits, the following ones set to 0.
     */
    private static InetAddress maskIP(InetAddress ip, int prefixLength) {
        if (!(ip instanceof Inet6Address)) {
            throw new IllegalArgumentException("Not an IPv6 address: " + ip);
        }
        int hostBits = MAX_PREFIX_LENGTH - prefixLength;
        BigInteger networkPart = InetAddresses.toBigInteger(ip).shiftRight(hostBits).shiftLeft(hostBits);
        return InetAddresses.fromIPv6BigInteger(networkPart);
    }
}
