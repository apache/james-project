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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.configuration.DefaultConfigurationBuilder;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.api.mock.MockDNSService;
import org.apache.james.domainlist.api.DomainListException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.google.common.collect.ImmutableList;

public class XMLDomainListTest {

    public static final String DEFAULT_DOMAIN = "default.domain";

    @Rule
    public ExpectedException expectedException = ExpectedException.none();
    
    private HierarchicalConfiguration setUpConfiguration(boolean auto, boolean autoIP, List<String> names) {
        DefaultConfigurationBuilder configuration = new DefaultConfigurationBuilder();

        configuration.addProperty("autodetect", auto);
        configuration.addProperty("autodetectIP", autoIP);
        for (String name : names) {
            configuration.addProperty("domainnames.domainname", name);
        }
        configuration.addProperty("defaultDomain", DEFAULT_DOMAIN);
        return configuration;
    }

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
        List<String> domains = new ArrayList<>();
        XMLDomainList dom = new XMLDomainList(setUpDNSServer("localhost"));
        dom.configure(setUpConfiguration(false, false, domains));

        assertThat(dom.getDomains()).containsOnly(DEFAULT_DOMAIN);
    }

    @Test
    public void testGetDomains() throws Exception {
        List<String> domains = new ArrayList<>();
        domains.add("domain1.");
        domains.add("domain2.");

        XMLDomainList dom = new XMLDomainList(setUpDNSServer("localhost"));
        dom.configure(setUpConfiguration(false, false, domains));

        assertThat(dom.getDomains()).hasSize(3);
    }

    @Test
    public void testGetDomainsAutoDetectNotLocalHost() throws Exception {
        List<String> domains = new ArrayList<>();
        domains.add("domain1.");

        XMLDomainList dom = new XMLDomainList(setUpDNSServer("local"));
        dom.configure(setUpConfiguration(true, false, domains));

        assertThat(dom.getDomains()).hasSize(3);
    }

    @Test
    public void testGetDomainsAutoDetectLocalHost() throws Exception {
        List<String> domains = new ArrayList<>();
        domains.add("domain1.");

        XMLDomainList dom = new XMLDomainList(setUpDNSServer("localhost"));
        dom.configure(setUpConfiguration(true, false, domains));

        assertThat(dom.getDomains()).hasSize(2);
    }

    @Test
    public void addDomainShouldFailWhenAlreadyConfigured() throws Exception {
        expectedException.expect(DomainListException.class);

        List<String> domains = new ArrayList<>();
        domains.add("domain1");

        XMLDomainList testee = new XMLDomainList(setUpDNSServer("hostname"));
        testee.configure(setUpConfiguration(true, false, domains));

        testee.addDomain("newDomain");
    }

    @Test
    public void removeDomainShouldFailWhenAlreadyConfigured() throws Exception {
        expectedException.expect(DomainListException.class);

        List<String> domains = new ArrayList<>();
        domains.add("domain1");

        XMLDomainList testee = new XMLDomainList(setUpDNSServer("localhost"));
        testee.configure(setUpConfiguration(true, false, domains));

        testee.removeDomain("newDomain");
    }

    @Test
    public void configureShouldNotFailWhenConfiguringDefaultDomain() throws Exception {
        DefaultConfigurationBuilder configuration = new DefaultConfigurationBuilder();

        configuration.addProperty("autodetect", false);
        configuration.addProperty("autodetectIP", false);
        configuration.addProperty("domainnames.domainname", "domain1");
        configuration.addProperty("defaultDomain", "localhost");

        XMLDomainList testee = new XMLDomainList(setUpDNSServer("localhost"));
        testee.configure(configuration);

        assertThat(testee.getDomainListInternal()).hasSize(3);
    }
}
