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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;

import org.apache.james.core.Domain;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.api.mock.MockDNSService;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.domainlist.lib.AutodetectDomainList;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

class XMLDomainListTest {
    static final Domain DEFAULT_DOMAIN = Domain.of("default.domain");
    static final Domain DOMAIN_1 = Domain.of("domain1");

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
    void testNoConfiguredDomains() throws Exception {
        DomainListConfiguration configuration = DomainListConfiguration.builder()
            .autoDetect(false)
            .autoDetectIp(false)
            .defaultDomain(DEFAULT_DOMAIN)
            .build();
        XMLDomainList xmlDomainList = new XMLDomainList(configuration);

        DomainList domainList = new AutodetectDomainList(setUpDNSServer("localhost"), xmlDomainList, configuration);


        assertThat(domainList.getDomains()).containsOnly(DEFAULT_DOMAIN);
    }

    @Test
    void testGetDomains() throws Exception {
        DomainListConfiguration configuration = DomainListConfiguration.builder()
            .autoDetect(false)
            .autoDetectIp(false)
            .addConfiguredDomains(Domain.of("domain1"), Domain.of("domain2"))
            .defaultDomain(DEFAULT_DOMAIN)
            .build();
        XMLDomainList xmlDomainList = new XMLDomainList(configuration);
        DomainList domainList = new AutodetectDomainList(setUpDNSServer("localhost"), xmlDomainList, configuration);

        assertThat(domainList.getDomains()).hasSize(3);
    }

    @Test
    void testGetDomainsAutoDetectNotLocalHost() throws Exception {
        DomainListConfiguration configuration = DomainListConfiguration.builder()
            .autoDetect(true)
            .autoDetectIp(false)
            .addConfiguredDomains(Domain.of("domain1"))
            .defaultDomain(DEFAULT_DOMAIN)
            .build();
        XMLDomainList xmlDomainList = new XMLDomainList(configuration);
        DomainList domainList = new AutodetectDomainList(setUpDNSServer("local"), xmlDomainList, configuration);

        assertThat(domainList.getDomains()).contains(Domain.of("local"));
    }

    @Test
    void testGetDomainsAutoDetectLocalHost() throws Exception {
        DomainListConfiguration configuration = DomainListConfiguration.builder()
            .autoDetect(false)
            .autoDetectIp(false)
            .addConfiguredDomains(Domain.of("domain1"))
            .defaultDomain(DEFAULT_DOMAIN)
            .build();
        XMLDomainList xmlDomainList = new XMLDomainList(configuration);
        DomainList testee = new AutodetectDomainList(setUpDNSServer("localhost"), xmlDomainList, configuration);

        assertThat(testee.getDomains()).hasSize(2);
    }

    @Test
    void addDomainShouldFailWhenAlreadyConfigured() throws Exception {
        DomainListConfiguration configuration = DomainListConfiguration.builder()
            .autoDetect(true)
            .autoDetectIp(false)
            .addConfiguredDomain(DOMAIN_1)
            .defaultDomain(DEFAULT_DOMAIN)
            .build();
        XMLDomainList xmlDomainList = new XMLDomainList(configuration);
        DomainList testee = new AutodetectDomainList(setUpDNSServer("hostname"), xmlDomainList, configuration);

        assertThatThrownBy(() -> testee.addDomain(Domain.of("newDomain")))
            .isInstanceOf(DomainListException.class);
    }

    @Test
    void removeDomainShouldFailWhenAlreadyConfigured() throws Exception {
        DomainListConfiguration configuration = DomainListConfiguration.builder()
            .autoDetect(true)
            .autoDetectIp(false)
            .addConfiguredDomain(DOMAIN_1)
            .build();
        XMLDomainList xmlDomainList = new XMLDomainList(configuration);
        DomainList testee = new AutodetectDomainList(setUpDNSServer("localhost"), xmlDomainList, configuration);

        assertThatThrownBy(() -> testee.removeDomain(Domain.of("newDomain")))
            .isInstanceOf(DomainListException.class);
    }

    @Test
    void configureShouldNotFailWhenConfiguringDefaultDomain() throws Exception {
        DomainListConfiguration configuration = DomainListConfiguration.builder()
            .autoDetect(false)
            .autoDetectIp(false)
            .defaultDomain(Domain.LOCALHOST)
            .addConfiguredDomain(DOMAIN_1)
            .build();

        XMLDomainList xmlDomainList = new XMLDomainList(configuration);
        DomainList testee = new AutodetectDomainList(setUpDNSServer("localhost"), xmlDomainList, configuration);

        assertThat(testee.getDomains())
            .containsOnly(DOMAIN_1, Domain.LOCALHOST);
    }
}
