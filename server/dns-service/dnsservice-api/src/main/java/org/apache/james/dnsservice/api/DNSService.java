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
package org.apache.james.dnsservice.api;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;

/**
 * Provides abstraction for DNS resolutions. The interface is Mail specific. It
 * may be a good idea to make the interface more generic or expose commonly
 * needed DNS methods.
 */
public interface DNSService {

    /**
     * <p>
     * Return a prioritized unmodifiable list of host handling mail for the
     * domain.
     * </p>
     * 
     * <p>
     * First lookup MX hosts, then MX hosts of the CNAME address, and if no
     * server is found return the IP of the hostname
     * </p>
     * 
     * @param hostname
     *            domain name to look up
     * 
     * @return a unmodifiable list of handling servers corresponding to this
     *         mail domain name
     * @throws TemporaryResolutionException
     *             get thrown on temporary problems
     */
    Collection<String> findMXRecords(String hostname) throws TemporaryResolutionException;

    /**
     * Get a collection of DNS TXT Records
     * 
     * @param hostname
     *            The hostname to check
     * @return collection of strings representing TXT record values
     */
    Collection<String> findTXTRecords(String hostname);

    /**
     * Resolve the given hostname to an array of InetAddress based on the DNS
     * Server. It should not take into account the hostnames defined in the
     * local host table
     * 
     * @return An array of InetAddress
     */
    InetAddress[] getAllByName(String host) throws UnknownHostException;

    /**
     * Resolve the given hostname to an InetAddress based on the DNS Server. It
     * should not take into account the hostnames defined in the local host
     * table
     * 
     * @return The resolved InetAddress or null if not resolved
     */
    InetAddress getByName(String host) throws UnknownHostException;

    /**
     * Resolve the local hostname of the machine and returns it. It relies on
     * the hostname defined in the local host table
     * 
     * @return The local InetAddress of the machine.
     */
    InetAddress getLocalHost() throws UnknownHostException;

    /**
     * Resolve the given InetAddress to an host name based on the DNS Server. It
     * should not take into account the hostnames defined in the local host
     * table
     * 
     * @return The resolved hostname String or null if not resolved
     */
    String getHostName(InetAddress addr);

}
