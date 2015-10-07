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
package org.apache.james.dnsservice.library.netmatcher;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.library.inetnetwork.InetNetworkBuilder;
import org.apache.james.dnsservice.library.inetnetwork.model.InetNetwork;

/**
 * NetMatcher Class is used to check if an ipAddress match a network.
 * 
 * NetMatcher provides a means for checking whether a particular IPv4 or IPv6
 * address or domain name is within a set of subnets.
 */
public class NetMatcher {

    /**
     * The DNS Service used to build InetNetworks.
     */
    private final DNSService dnsServer;

    /**
     * The Set of InetNetwork to match against.
     */
    private SortedSet<InetNetwork> networks;

    /**
     * Create a new instance of Netmatcher.
     * 
     * @param nets
     *            a String[] which holds all networks
     * @param dnsServer
     *            the DNSService which will be used in this class
     */
    public NetMatcher(final String[] nets, DNSService dnsServer) {
        this.dnsServer = dnsServer;
        initInetNetworks(nets);
    }

    /**
     * Create a new instance of Netmatcher.
     * 
     * @param nets
     *            a Collection which holds all networks
     * @param dnsServer
     *            the DNSService which will be used in this class
     */
    public NetMatcher(final Collection<String> nets, DNSService dnsServer) {
        this.dnsServer = dnsServer;
        initInetNetworks(nets);
    }

    /**
     * The given String may represent an IP address or a host name.
     * 
     * @param hostIP
     *            the ipAddress or host name to check
     * @see #matchInetNetwork(InetAddress)
     */
    public boolean matchInetNetwork(final String hostIP) {

        InetAddress ip;

        try {
            ip = dnsServer.getByName(hostIP);
        } catch (UnknownHostException uhe) {
            log("Cannot resolve address for " + hostIP + ": " + uhe.getMessage());
            return false;
        }

        return matchInetNetwork(ip);

    }

    /**
     * Return true if passed InetAddress match a network which was used to
     * construct the Netmatcher.
     * 
     * @param ip
     *            InetAddress
     * @return true if match the network
     */
    public boolean matchInetNetwork(final InetAddress ip) {

        boolean sameNet = false;

        for (Iterator<InetNetwork> iter = networks.iterator(); (!sameNet) && iter.hasNext();) {
            InetNetwork network = iter.next();
            sameNet = network.contains(ip);
        }

        return sameNet;

    }

    @Override
    public String toString() {
        return networks.toString();
    }

    /**
     * Can be overwritten for logging
     *
     * @param s
     *            the String to log
     */
    protected void log(String s) {
    }

    /**
     * Init the class with the given networks.
     * 
     * @param nets
     *            a Collection which holds all networks
     */
    private void initInetNetworks(final Collection<String> nets) {
        initInetNetworks(nets.toArray(new String[nets.size()]));
    }

    /**
     * Init the class with the given networks.
     * 
     * @param nets
     *            a String[] which holds all networks
     */
    private void initInetNetworks(final String[] nets) {

        networks = new TreeSet<InetNetwork>(new Comparator<InetNetwork>() {
            public int compare(InetNetwork in1, InetNetwork in2) {
                return in1.toString().compareTo(in2.toString());
            }
        });

        final InetNetworkBuilder inetNetwork = new InetNetworkBuilder(dnsServer);

        for (String net : nets) {
            try {
                InetNetwork inet = inetNetwork.getFromString(net);
                networks.add(inet);
            } catch (UnknownHostException uhe) {
                log("Cannot resolve address: " + uhe.getMessage());
            }
        }

    }

}
