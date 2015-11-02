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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.api.mock.MockDNSService;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public abstract class AbstractDomainListTest {

    private final String DOMAIN_1 = "domain1.tld";
    private final String DOMAIN_2 = "domain2.tld";
    private final String DOMAIN_3 = "domain3.tld";
    private final String DOMAIN_4 = "domain4.tld";
    private final String DOMAIN_5 = "domain5.tld";
    private final String DOMAIN_UPPER_5 = "Domain5.tld";

    private DomainList domainList;

    @Before
    public void setUp() throws Exception {
        domainList = createDomainList();
    }

    @After
    public void tearDown() throws Exception {
        deleteAll();
    }

    @Test
    public void createListDomains() throws DomainListException {
        domainList.addDomain(DOMAIN_3);
        domainList.addDomain(DOMAIN_4);
        domainList.addDomain(DOMAIN_5);
        assertThat(domainList.getDomains()).containsOnly(DOMAIN_3, DOMAIN_4, DOMAIN_5);
    }

    @Test
    public void domainsShouldBeListedInLowerCase() throws DomainListException {
        domainList.addDomain(DOMAIN_UPPER_5);
        assertThat(domainList.getDomains()).containsOnly(DOMAIN_5);
    }

    @Test
    public void containShouldReturnTrueWhenThereIsADomain() throws DomainListException {
        domainList.addDomain(DOMAIN_2);
        assertThat(domainList.containsDomain(DOMAIN_2)).isTrue();
    }

    @Test
    public void containShouldBeCaseSensitive() throws DomainListException {
        domainList.addDomain(DOMAIN_5);
        assertThat(domainList.containsDomain(DOMAIN_UPPER_5)).isTrue();
    }

    @Test
    public void listDomainsShouldReturnNullWhenThereIsNoDomains() throws DomainListException {
        assertThat(domainList.getDomains()).isNull();
    }

    @Test
    public void testAddRemoveContainsSameDomain() throws DomainListException {
        domainList.addDomain(DOMAIN_1);
        domainList.removeDomain(DOMAIN_1);
        assertThat(domainList.getDomains()).isNull();
    }

    @Test(expected = DomainListException.class)
    public void addShouldBeCaseSensitive() throws DomainListException {
        try {
            domainList.addDomain(DOMAIN_5);
        } catch (Exception e) {
            fail(e.getMessage());
        }
        domainList.addDomain(DOMAIN_UPPER_5);
    }

    @Test
    public void deletingADomainShouldNotDeleteOtherDomains() throws DomainListException {
        domainList.addDomain(DOMAIN_1);
        try {
            domainList.removeDomain(DOMAIN_2);
        } catch (DomainListException e) {

        }
        assertThat(domainList.getDomains()).containsOnly(DOMAIN_1);
    }

    @Test
    public void containShouldReturnFalseWhenThereIsNoDomain() throws DomainListException {
        assertThat(domainList.containsDomain(DOMAIN_1)).isFalse();
    }

    @Test
    public void ContainsShouldReturnFalseWhenDomainIsRemoved() throws DomainListException {
        domainList.addDomain(DOMAIN_1);
        domainList.removeDomain(DOMAIN_1);
        assertThat(domainList.containsDomain(DOMAIN_1)).isFalse();
    }

    @Test
    public void RemoveShouldRemoveDomainsUsingUpperCases() throws DomainListException {
        domainList.addDomain(DOMAIN_UPPER_5);
        domainList.removeDomain(DOMAIN_UPPER_5);
        assertThat(domainList.containsDomain(DOMAIN_UPPER_5)).isFalse();
    }

    @Test
    public void RemoveShouldRemoveDomainsUsingLowerCases() throws DomainListException {
        domainList.addDomain(DOMAIN_UPPER_5);
        domainList.removeDomain(DOMAIN_5);
        assertThat(domainList.containsDomain(DOMAIN_UPPER_5)).isFalse();
    }

    @Test(expected = DomainListException.class)
    public void addDomainShouldThrowIfWeAddTwoTimesTheSameDomain() throws DomainListException {
        try {
            domainList.addDomain(DOMAIN_1);
        } catch (Exception e) {
            fail(e.getMessage());
        }
        domainList.addDomain(DOMAIN_1);
    }

    @Test(expected = DomainListException.class)
    public void removeDomainShouldThrowIfTheDomainIsAbsent() throws DomainListException {
        domainList.removeDomain(DOMAIN_1);
    }

    /**
     * Delete all possible domains from database.
     */
    private void deleteAll() {
        deleteWithoutError(DOMAIN_1);
        deleteWithoutError(DOMAIN_2);
        deleteWithoutError(DOMAIN_3);
        deleteWithoutError(DOMAIN_4);
        deleteWithoutError(DOMAIN_5);
    }

    private void deleteWithoutError(String domain) {
        try {
            domainList.removeDomain(domain);
        } catch(DomainListException e) {

        }
    }

    /**
     * Return a fake DNSServer.
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
