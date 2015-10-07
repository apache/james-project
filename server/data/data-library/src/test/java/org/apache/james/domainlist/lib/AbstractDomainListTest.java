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
package org.apache.james.domainlist.lib;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.api.mock.MockDNSService;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

/**
 * Test the implementation of the DomainList.
 */
public abstract class AbstractDomainListTest {

    // Domains we will play with.
    private final String DOMAIN_1 = "domain1.tld";
    private final String DOMAIN_2 = "domain2.tld";
    private final String DOMAIN_3 = "domain3.tld";
    private final String DOMAIN_4 = "domain4.tld";
    private final String DOMAIN_5 = "domain5.tld";
    /**
     * The JPA DomainList service.
     */
    private DomainList domainList;

    @Before
    public void setUp() throws Exception {
        domainList = createDomainList();
        deleteAll();
    }

    /**
     * Add 3 domains and list them.
     * 
     * @throws DomainListException
     */
    @Test
    public void createListDomains() throws DomainListException {
        domainList.addDomain(DOMAIN_3);
        domainList.addDomain(DOMAIN_4);
        domainList.addDomain(DOMAIN_5);
        assertEquals(3, domainList.getDomains().length);
    }

    /**
     * Add a domain and check it is present.
     * 
     * @throws DomainListException
     */
    @Test
    public void testAddContainsDomain() throws DomainListException {
        domainList.addDomain(DOMAIN_2);
        domainList.containsDomain(DOMAIN_2);
    }

    /**
     * Add and remove a domain, and check database is empty.
     * 
     * @throws DomainListException
     */
    @Test
    public void testAddRemoveContainsSameDomain() throws DomainListException {
        domainList.addDomain(DOMAIN_1);
        domainList.removeDomain(DOMAIN_1);
        assertThat(domainList.getDomains(), nullValue());
    }

    /**
     * Add two same domains with different cases, and check we've got an exception.
     * 
     * @throws DomainListException
     */
    @Test
    public void testUpperCaseSameDomain() throws DomainListException {
        domainList.addDomain(DOMAIN_1);
        assertEquals(1, domainList.getDomains().length);
        try {
            String DOMAIN_1_UPPER_CASE = "Domain1.tld";
            domainList.addDomain(DOMAIN_1_UPPER_CASE);
            fail("We should not be able to insert same domains, even with different cases");
        } catch (DomainListException domainListException) {
            assertTrue(domainListException.getMessage().contains(DOMAIN_1));
        }
    }

    /**
     * Add a domain and remove another domain, and check first domain is still
     * present.
     * 
     * @throws DomainListException
     */
    @Test
    public void testAddRemoveContainsDifferentDomain() throws DomainListException {
        domainList.addDomain(DOMAIN_1);
        domainList.removeDomain(DOMAIN_2);
        assertEquals(1, domainList.getDomains().length);
        assertEquals(true, domainList.containsDomain(DOMAIN_1));
    }

    /**
     * Delete all possible domains from database.
     * 
     * @throws DomainListException
     */
    private void deleteAll() throws DomainListException {
        domainList.removeDomain(DOMAIN_1);
        domainList.removeDomain(DOMAIN_2);
        domainList.removeDomain(DOMAIN_3);
        domainList.removeDomain(DOMAIN_4);
        domainList.removeDomain(DOMAIN_5);
    }

    /**
     * Return a fake DNSServer.
     * 
     * @param hostName
     * @return
     */
    protected DNSService getDNSServer(final String hostName) {
        return new MockDNSService() {

            @Override
            public String getHostName(InetAddress inet) {
                return hostName;
            }

            @Override
            public InetAddress[] getAllByName(String name) throws UnknownHostException {
                return new InetAddress[]{InetAddress.getByName("127.0.0.1")};
            }

            @Override
            public InetAddress getLocalHost() throws UnknownHostException {
                return InetAddress.getLocalHost();
            }
        };
    }

    /**
     * Implementing test classes must provide the corresponding implement
     * of the DomainList interface.
     * 
     * @return an implementation of DomainList
     */
    protected abstract DomainList createDomainList();
}
