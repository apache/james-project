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
package org.apache.james.dnsservice.library.inetnetwork;

import java.net.UnknownHostException;

import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.library.inetnetwork.model.Inet4Network;
import org.apache.james.dnsservice.library.inetnetwork.model.Inet6Network;
import org.apache.james.dnsservice.library.inetnetwork.model.InetNetwork;

/**
 * <p>
 * Builds a InetNetwork (Inet4Network or Inet6Network) in function on the
 * provided string pattern that represents a subnet.
 * </p>
 * 
 * <p>
 * Inet4Network is constructed based on the IPv4 subnet expressed in one of
 * several formats:
 * 
 * <pre>
 *     IPv4 Format                     Example
 *     Explicit address                127.0.0.1
 *     Address with a wildcard         127.0.0.*
 *     Domain name                     myHost.com
 *     Domain name + prefix-length     myHost.com/24
 *     Domain name + mask              myHost.com/255.255.255.0
 *     IP address + prefix-length      127.0.0.0/8
 *     IP + mask                       127.0.0.0/255.0.0.0
 * </pre>
 * 
 * For more information on IP V4, see RFC 1518 and RFC 1519.
 * </p>
 * 
 * <p>
 * Inet6Network is constructed based on the IPv4 subnet expressed in one of
 * several formats:
 * 
 * <pre>
 *     IPv6 Format                     Example
 *     Explicit address                0000:0000:0000:0000:0000:0000:0000:0001
 *     IP address + subnet mask (/)   0000:0000:0000:0000:0000:0000:0000:0001/64
 *     IP address + subnet mask (%)   0000:0000:0000:0000:0000:0000:0000:0001%64
 *     The following V6 formats will be supported later:
 *     Domain name                     myHost.com
 *     Domain name + mask (/)          myHost.com/48
 *     Domain name + mask (%)          myHost.com%48
 *     Explicit shorted address        ::1
 * </pre>
 * 
 * For more information on IP V6, see RFC 2460. (See also <a
 * href="http://en.wikipedia.org/wiki/IPv6_address"
 * >http://en.wikipedia.org/wiki/IPv6_address</a>)
 * </p>
 */
public class InetNetworkBuilder {

    /**
     * The DNS Server used to create InetAddress for hostnames and IP adresses.
     */
    private final DNSService dnsService;

    /**
     * Constructs a InetNetwork.
     * 
     * @param dnsServer
     *            the DNSService to use
     */
    public InetNetworkBuilder(DNSService dnsServer) {
        this.dnsService = dnsServer;
    }

    /**
     * Creates a InetNetwork for the given String. Depending on the provided
     * pattern and the platform configuration (IPv4 and/or IPv6), the returned
     * type will be Inet4Network or Inet6Network.
     * 
     * @param netspec
     *            the String which is will converted to InetNetwork
     * @return network the InetNetwork
     * @throws java.net.UnknownHostException
     */
    public InetNetwork getFromString(String netspec) throws UnknownHostException {
        return isV6(netspec) ? getV6FromString(netspec) : getV4FromString(netspec);
    }

    /**
     * Returns true if the string parameters is a IPv6 pattern. Currently, only
     * tests for presence of ':'.
     * 
     * @param netspec
     * @return boolean
     *              <code>true</code> if is a IPv6 pattern else <code>false</code>
     */
    public static boolean isV6(String netspec) {
        return netspec.contains(":");
    }

    /**
     * Get a Inet4Network for the given String.
     * 
     * @param netspec
     *            the String which is will converted to InetNetwork
     * @return network the InetNetwork
     * @throws java.net.UnknownHostException
     */
    private InetNetwork getV4FromString(String netspec) throws UnknownHostException {

        if (netspec.endsWith("*")) {
            netspec = normalizeV4FromAsterisk(netspec);
        } else {
            int iSlash = netspec.indexOf('/');
            if (iSlash == -1) {
                netspec += "/255.255.255.255";
            } else if (netspec.indexOf('.', iSlash) == -1) {
                netspec = normalizeV4FromCIDR(netspec);
            }
        }

        return new Inet4Network(dnsService.getByName(netspec.substring(0, netspec.indexOf('/'))), dnsService.getByName(netspec.substring(netspec.indexOf('/') + 1)));
    }

    /**
     * Get a Inet6Network for the given String.
     * 
     * @param netspec
     *            the String which is will converted to InetNetwork
     * @return network the InetNetwork
     * @throws java.net.UnknownHostException
     */
    private InetNetwork getV6FromString(String netspec) throws UnknownHostException {

        if (netspec.endsWith("*")) {
            throw new UnsupportedOperationException("Wildcard for IPv6 not supported");
        }

        // Netmask can be separated with %
        netspec = netspec.replaceAll("%", "/");

        if (netspec.indexOf('/') == -1) {
            netspec += "/32768";
        }

        return new Inet6Network(dnsService.getByName(netspec.substring(0, netspec.indexOf('/'))), Integer.valueOf(netspec.substring(netspec.indexOf('/') + 1)));
    }

    /**
     * This converts from an uncommon "wildcard" CIDR format to "address + mask"
     * format:
     * 
     * <pre>
     * * => 000.000.000.0/000.000.000.0 
     * xxx.* => xxx.000.000.0/255.000.000.0
     * xxx.xxx.* => xxx.xxx.000.0/255.255.000.0 
     * xxx.xxx.xxx.* => xxx.xxx.xxx.0/255.255.255.0
     * </pre>
     * 
     * @param netspec
     * @return addrMask the address/mask of the given argument
     */
    private static String normalizeV4FromAsterisk(String netspec) {

        String[] masks = { "0.0.0.0/0.0.0.0", "0.0.0/255.0.0.0", "0.0/255.255.0.0", "0/255.255.255.0" };

        char[] srcb = netspec.toCharArray();

        int octets = 0;

        for (int i = 1; i < netspec.length(); i++) {
            if (srcb[i] == '.') {
                octets++;
            }
        }

        return (octets == 0) ? masks[0] : netspec.substring(0, netspec.length() - 1).concat(masks[octets]);

    }

    /**
     * RFC 1518, 1519 - Classless Inter-Domain Routing (CIDR) This converts from
     * "prefix + prefix-length" format to "address + mask" format, e.g. from
     * 
     * <pre>
     * xxx.xxx.xxx.xxx / yy
     * </pre>
     * 
     * to
     * 
     * <pre>
     * xxx.xxx.xxx.xxx / yyy.yyy.yyy.yyy
     * </pre>
     * 
     * .
     * 
     * @param netspec
     *            the xxx.xxx.xxx.xxx/yyy format
     * @return addrMask the xxx.xxx.xxx.xxx/yyy.yyy.yyy.yyy format
     */
    private static String normalizeV4FromCIDR(String netspec) {

        final int bits = 32 - Integer.parseInt(netspec.substring(netspec.indexOf('/') + 1));

        final int mask = (bits == 32) ? 0 : 0xFFFFFFFF - ((1 << bits) - 1);

        return netspec.substring(0, netspec.indexOf('/') + 1) + Integer.toString(mask >> 24 & 0xFF, 10) + "." + Integer.toString(mask >> 16 & 0xFF, 10) + "." + Integer.toString(mask >> 8 & 0xFF, 10) + "." + Integer.toString(mask & 0xFF, 10);
    }

}
