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
package org.apache.james.domainlist.xml;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;

import org.apache.james.core.Domain;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.api.mock.MockDNSService;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.google.common.collect.ImmutableList;

public class XMLDomainListTest {

    public static final Domain DEFAULT_DOMAIN = Domain.of("default.domain");
    public static final Domain DOMAIN_1 = Domain.of("domain1");

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private DNSService setUpDNSServer(final String hostName) {
        return new MockDNSService() {

            @Override
            public String getHostName(InetAddress inet) {
                return hostName;
            }

            @Override
            public Collection<InetAddress> getAllByName(String name) throws UnknownHostException {
                return ImmutableList.of(InetAddress.getByName("127.0.0.1"));
            }

            @Override
            public InetAddress getLocalHost() throws UnknownHostException {
                return InetAddress.getLocalHost();
            }
        };
    }

    // See https://issues.apache.org/jira/browse/JAMES-998
    @Test
    public void testNoConfiguredDomains() throws Exception {
        XMLDomainList dom = new XMLDomainList(setUpDNSServer("localhost"));

        dom.configure(DomainListConfiguration.builder()
            .autoDetect(false)
            .autoDetectIp(false)
            .defaultDomain(DEFAULT_DOMAIN));


        assertThat(dom.getDomains()).containsOnly(DEFAULT_DOMAIN);
    }

    @Test
    public void testGetDomains() throws Exception {
        XMLDomainList dom = new XMLDomainList(setUpDNSServer("localhost"));
        dom.configure(DomainListConfiguration.builder()
            .autoDetect(false)
            .autoDetectIp(false)
            .addConfiguredDomains(Domain.of("domain1."), Domain.of("domain2."))
            .defaultDomain(DEFAULT_DOMAIN));

        assertThat(dom.getDomains()).hasSize(3);
    }

    @Test
    public void testGetDomainsAutoDetectNotLocalHost() throws Exception {
        XMLDomainList dom = new XMLDomainList(setUpDNSServer("local"));
        dom.configure(DomainListConfiguration.builder()
            .autoDetect(true)
            .autoDetectIp(false)
            .addConfiguredDomains(Domain.of("domain1."))
            .defaultDomain(DEFAULT_DOMAIN));

        assertThat(dom.getDomains()).hasSize(3);
    }

    @Test
    public void testGetDomainsAutoDetectLocalHost() throws Exception {
        XMLDomainList dom = new XMLDomainList(setUpDNSServer("localhost"));
        dom.configure(DomainListConfiguration.builder()
            .autoDetect(false)
            .autoDetectIp(false)
            .addConfiguredDomains(Domain.of("domain1."))
            .defaultDomain(DEFAULT_DOMAIN));

        assertThat(dom.getDomains()).hasSize(2);
    }

    @Test
    public void addDomainShouldFailWhenAlreadyConfigured() throws Exception {
        expectedException.expect(DomainListException.class);

        XMLDomainList testee = new XMLDomainList(setUpDNSServer("hostname"));
        testee.configure(DomainListConfiguration.builder()
            .autoDetect(true)
            .autoDetectIp(false)
            .addConfiguredDomain(DOMAIN_1)
            .defaultDomain(DEFAULT_DOMAIN));

        testee.addDomain(Domain.of("newDomain"));
    }

    @Test
    public void removeDomainShouldFailWhenAlreadyConfigured() throws Exception {
        expectedException.expect(DomainListException.class);

        XMLDomainList testee = new XMLDomainList(setUpDNSServer("localhost"));
        testee.configure(DomainListConfiguration.builder()
            .autoDetect(true)
            .autoDetectIp(false)
            .addConfiguredDomain(DOMAIN_1));

        testee.removeDomain(Domain.of("newDomain"));
    }

    @Test
    public void configureShouldNotFailWhenConfiguringDefaultDomain() throws Exception {
        XMLDomainList testee = new XMLDomainList(setUpDNSServer("localhost"));
        testee.configure(DomainListConfiguration.builder()
            .autoDetect(false)
            .autoDetectIp(false)
            .defaultDomain(Domain.LOCALHOST)
            .addConfiguredDomain(DOMAIN_1));

        assertThat(testee.getDomainListInternal())
            .containsOnly(DOMAIN_1, Domain.LOCALHOST);
    }
}
