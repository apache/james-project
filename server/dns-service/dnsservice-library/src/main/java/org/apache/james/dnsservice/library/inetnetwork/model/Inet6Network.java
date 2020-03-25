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

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

import org.apache.james.dnsservice.library.inetnetwork.InetNetworkBuilder;


public class Inet6Network implements InetNetwork {

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

    private static InetAddress maskIP(InetAddress ip, Integer mask) {
        byte[] maskBytes = new byte[16];
        int i = 0;
        while (mask > 0) {
            maskBytes[i] = (byte) 255;
            i++;
            mask = (mask >> 1);
        }
        return maskIP(ip.getAddress(), maskBytes);
    }

    /**
     * Return InetAddress generated of the passed arguments. Return Null if any
     * error occurs
     *
     * @param ip   the byte[] represent the ip
     * @param mask the byte[] represent the netmask
     * @return inetAddress the InetAddress generated of the passed arguments.
     */
    private static InetAddress maskIP(byte[] ip, byte[] mask) {
        if (ip.length != mask.length) {
            throw new IllegalArgumentException("IP address and mask must be of the same length.");
        }
        if (ip.length != 16) {
            throw new IllegalArgumentException("IP address and mask length must be equal to 16.");
        }
        try {
            byte[] maskedIp = new byte[ip.length];
            for (int i = 0; i < ip.length; i++) {
                maskedIp[i] = (byte) (ip[i] & mask[i]);
            }
            return getByAddress(maskedIp);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    /**
     * Return InetAddress which represent the given byte[]
     *
     * @param ip the byte[] represent the ip
     * @return ip the InetAddress generated of the given byte[]
     * @throws java.net.UnknownHostException
     */
    private static InetAddress getByAddress(byte[] ip) throws UnknownHostException {

        InetAddress addr = Inet6Address.getByAddress(ip);

        // TODO Don't know if this is correct?
        if (addr == null) {
            addr = InetAddress.getByName(Integer.toString(ip[0] & 0xFF, 10) + ":" + Integer.toString(ip[1] & 0xFF, 10) + ":" + Integer.toString(ip[2] & 0xFF, 10) + ":" + Integer.toString(ip[3] & 0xFF, 10) + ":" + Integer.toString(ip[4] & 0xFF, 10) + ":" + Integer.toString(ip[5] & 0xFF, 10) + ":"
                    + Integer.toString(ip[6] & 0xFF, 10) + ":" + Integer.toString(ip[7] & 0xFF, 10) + ":" + Integer.toString(ip[8] & 0xFF, 10) + ":" + Integer.toString(ip[9] & 0xFF, 10) + ":" + Integer.toString(ip[10] & 0xFF, 10) + ":" + Integer.toString(ip[11] & 0xFF, 10) + ":"
                    + Integer.toString(ip[12] & 0xFF, 10) + ":" + Integer.toString(ip[13] & 0xFF, 10) + ":" + Integer.toString(ip[14] & 0xFF, 10) + ":" + Integer.toString(ip[15] & 0xFF, 10));
        }

        return addr;
    }

}
