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

import java.net.InetAddress;

/**
 * An InetNetwork represents a IPv4 or IPv6 address with a subnet mask.<br>
 * The subnet mask allows to represent one or more host (a "network of hosts").
 * 
 * Do not confuse the InetAddress.toString() returning a "hostname/ip_address"
 * (optional hostname) with the InetNetwork.toString() that returns a
 * "ip_address/subnet_mask".
 */
public interface InetNetwork {

    /**
     * Return true if the network contains the given name
     * 
     * @param ip
     *            hostname or ipAddress
     * @return true if the network contains the ip address
     */
    boolean contains(InetAddress ip);

}
