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

import static org.assertj.core.api.Assertions.assertThat;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.StringTokenizer;

import org.junit.jupiter.api.Test;

/**
 * Test the InetNetwork class with various IPv4 and IPv6 network specification.
 */
class InetNetworkTest {

    private static InetAddress address;

    /**
     * Preliminary test method to validate the InetAddress behaviour (V4 and V6
     * name and address).
     * 
     * The InetAddress.toString() returns a string with format
     * "name/ip_address". It has no indication of subnetmask. The name is
     * optional.
     */
    @Test
    void testInetAddress() throws UnknownHostException {

        // Test name alone (can be IPv4 or IPv6 depending on the OS plaform
        // configuration).
        address = InetAddress.getByName("localhost");
        assertThat(address).isInstanceOf(InetAddress.class);
        assertThat(address.toString().contains("localhost")).isTrue();

    }

    /**
     * Test method to validate the Inet4Address behavior (name and address).
     * 
     * The InetAddress.toString() returns a string with format
     * "name/ip_address". It has no indication of subnetmask. The returned name
     * is optional.
     */
    @Test
    void testInet4Address() throws UnknownHostException {

        // Test Bad IP V4 address.
        try {
            address = InetAddress.getByAddress(getBytesFromAddress("127.0.0.1.1"));
            assertThat(false).isTrue();
        } catch (UnknownHostException e) {
            assertThat(true).isTrue();
        }

        // Test IP V4 address.
        address = InetAddress.getByAddress(getBytesFromAddress("127.0.0.1"));
        assertThat(address).isInstanceOf(Inet4Address.class);
        assertThat(address.toString().contains("/127.0.0.1")).isTrue();

        // Test IP V4 with 255 values (just 'like' a subnet mask).
        address = InetAddress.getByAddress(getBytesFromAddress("255.255.225.0"));
        assertThat(address).isInstanceOf(Inet4Address.class);
        assertThat(address.toString().contains("/255.255.225.0")).isTrue();

        // Test IP V4 Address with name and IP address.
        address = InetAddress.getByAddress("localhost", getBytesFromAddress("127.0.0.1"));
        assertThat(address).isInstanceOf(Inet4Address.class);
        assertThat(address.toString().contains("localhost")).isTrue();
        assertThat(address.toString().contains("/127.0.0.1")).isTrue();
    }

    /**
     * Test method to validate the Inet6Address behavior (name and address).
     * 
     * The InetAddress.toString() returns a string with format
     * "name/ip_address". It has no indication of subnetmask. The returned name
     * is optional.
     */
    @Test
    void testInet6Address() throws UnknownHostException {

        // Test Bad IP V6 address.
        try {
            address = InetAddress.getByAddress(getBytesFromAddress("0000:0000:0000:0000:0000:0000:0000:0001:00001"));
            assertThat(false).isTrue();
        } catch (UnknownHostException e) {
            assertThat(true).isTrue();
        }

        // Test IP V6 address.
        address = InetAddress.getByAddress(getBytesFromAddress("0000:0000:0000:0000:0000:0000:0000:0001"));
        assertThat(address).isInstanceOf(Inet6Address.class);
        assertThat(address.toString().contains("/0:0:0:0:0:0:0:1")).isTrue();

        // Test IP V6 Address with name and IP address.
        address = InetAddress.getByAddress("localhost", getBytesFromAddress("0000:0000:0000:0000:0000:0000:0000:0001"));
        assertThat(address).isInstanceOf(Inet6Address.class);
        assertThat(address.toString().contains("localhost")).isTrue();
        assertThat(address.toString().contains("/0:0:0:0:0:0:0:1")).isTrue();

    }

    /**
     * Test the Inet4Network.
     */
    @Test
     void testInet4Network() throws UnknownHostException {

        // Test with null parameter.
        address = InetAddress.getByAddress(getBytesFromAddress("127.0.0.1"));
        Inet4Network network4;
        try {
            network4 = new Inet4Network(address, null);
            assertThat(false).isTrue();
        } catch (NullPointerException e) {
            assertThat(true).isTrue();
        }

        // Test IP V4.
        address = InetAddress.getByAddress(getBytesFromAddress("127.0.0.1"));
        InetAddress subnetmask4 = InetAddress.getByAddress(getBytesFromAddress("255.255.255.0"));
        network4 = new Inet4Network(address, subnetmask4);
        assertThat(network4.toString()).isEqualTo("127.0.0.0/255.255.255.0");
    }

    /**
     * Test the Inet6Network.
     */
    @Test
    void testInet6Network() throws UnknownHostException {

        // Test with null parameter.
        address = InetAddress.getByAddress(getBytesFromAddress("2781:0db8:1234:8612:45ee:0000:f05e:0001"));
        Inet6Network network6;
        try {
            network6 = new Inet6Network(address, null);
            assertThat(false).isTrue();
        } catch (NullPointerException e) {
            assertThat(true).isTrue();
        }

        // Test IP V6 with subnet mask 32768.
        address = InetAddress.getByAddress(getBytesFromAddress("2781:0db8:1234:8612:45ee:0000:f05e:0001"));
        Integer subnetmask6 = 32768;
        network6 = new Inet6Network(address, subnetmask6);
        assertThat(network6.toString()).isEqualTo("2781:db8:1234:8612:45ee:0:f05e:1/32768");

        // Test IP V6 with subnet mask 128.
        address = InetAddress.getByAddress(getBytesFromAddress("2781:0db8:1234:8612:45ee:0000:f05e:0001"));
        subnetmask6 = 128;
        network6 = new Inet6Network(address, subnetmask6);
        assertThat(network6.toString()).isEqualTo("2781:db8:1234:8612:0:0:0:0/128");

        // Test IP V6 with subnet mask 48.
        address = InetAddress.getByAddress(getBytesFromAddress("2781:0db8:1234:8612:45ee:0000:f05e:0001"));
        subnetmask6 = 48;
        network6 = new Inet6Network(address, subnetmask6);
        assertThat(network6.toString()).isEqualTo("2781:db8:1234:0:0:0:0:0/48");

        // Test IP V6 with subnet mask 16.
        address = InetAddress.getByAddress(getBytesFromAddress("2781:0db8:1234:8612:45ee:0000:f05e:0001"));
        subnetmask6 = 16;
        network6 = new Inet6Network(address, subnetmask6);
        assertThat(network6.toString()).isEqualTo("2781:db8:1200:0:0:0:0:0/16");

        // Test IP V6 with subnet mask 2.
        address = InetAddress.getByAddress(getBytesFromAddress("2781:0db8:1234:8612:45ee:0000:f05e:0001"));
        subnetmask6 = 2;
        network6 = new Inet6Network(address, subnetmask6);
        assertThat(network6.toString()).isEqualTo("2781:0:0:0:0:0:0:0/2");

        // Test IP V6 with subnet mask 1.
        address = InetAddress.getByAddress(getBytesFromAddress("2781:0db8:1234:8612:45ee:0000:f05e:0001"));
        subnetmask6 = 1;
        network6 = new Inet6Network(address, subnetmask6);
        assertThat(network6.toString()).isEqualTo("2700:0:0:0:0:0:0:0/1");

        // Test IP V6 with subnet mask 0.
        address = InetAddress.getByAddress(getBytesFromAddress("2781:0db8:1234:8612:45ee:0000:f05e:0001"));
        subnetmask6 = 0;
        network6 = new Inet6Network(address, subnetmask6);
        assertThat(network6.toString()).isEqualTo("0:0:0:0:0:0:0:0/0");
    }

    /**
     * Returns the bytes representation of an IP address.<br>
     * <br>
     * The address must respect "xyz.xyz.xyz.xyz" format for IP V4 ("127.0.0.1",
     * "172.16.1.38",..)
     * 
     * or wxyz:wxyz:wxyz:wxyz:wxyz:wxyz:wxyz:wxyz for IP V6 <br>
     * ("0000:0000:0000:0000:0000:0000:0000:0001"<br>
     * or "2001:0db8:85a3:0000:0000:8a2e:0370:7334").
     * 
     * @return the byte array representation of the ip address.
     */
    private byte[] getBytesFromAddress(String address) {

        if (address.contains(".")) {
            StringTokenizer st = new StringTokenizer(address, ".");
            byte[] bytes = new byte[st.countTokens()];
            int i = 0;
            while (st.hasMoreTokens()) {
                Integer inb = Integer.parseInt(st.nextToken());
                bytes[i] = inb.byteValue();
                i++;
            }
            return bytes;
        } else if (address.contains(":")) {
            StringTokenizer st = new StringTokenizer(address, ":");
            byte[] bytes = new byte[st.countTokens() * 2];
            int i = 0;
            while (st.hasMoreTokens()) {
                String token = st.nextToken();
                bytes[i] = (byte) Integer.parseInt(token.substring(0, 2), 16);
                i++;
                bytes[i] = (byte) Integer.parseInt(token.substring(2, 4), 16);
                i++;
            }
            return bytes;
        }

        throw new IllegalArgumentException(
                "The address [" + address + "] is not of the correct format. It should at least contain a . or a :");
    }
}
