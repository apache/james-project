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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.util.List;

import org.apache.james.core.Domain;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.api.AutoDetectedDomainRemovalException;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

class AbstractDomainListPrivateMethodsTest {
    DNSService dnsService;
    EnvDetector envDetector;

    public DomainList testee(DomainListConfiguration configuration) throws Exception {
        return testee(new MyDomainList(), configuration);
    }

    public DomainList testee(DomainListConfiguration.Builder configuration) throws Exception {
        return testee(new MyDomainList(), configuration.build());
    }

    public DomainList testee(MyDomainList domainList, DomainListConfiguration configuration) throws Exception {
        domainList.configure(configuration);
        new DomainCreator(domainList, configuration).createConfiguredDomains();
        return new AutodetectDomainList(dnsService, domainList, configuration);
    }

    @BeforeEach
    void setup() {
        dnsService = mock(DNSService.class);
        envDetector = mock(EnvDetector.class);
    }

    private static class MyDomainList extends AbstractDomainList {

        private List<Domain> domains;

        MyDomainList() {
            this.domains = Lists.newArrayList();
        }

        @Override
        protected boolean containsDomainInternal(Domain domain) {
            return domains.contains(domain);
        }

        @Override
        public void addDomain(Domain domain) throws DomainListException {
            if (domains.contains(domain)) {
                throw new DomainListException("Domain already exists!");
            }
            domains.add(domain);
        }

        @Override
        public void doRemoveDomain(Domain domain) {
            domains.remove(domain);
        }

        @Override
        protected List<Domain> getDomainListInternal() {
            return domains;
        }
    }

    @Test
    void setDefaultDomainShouldSetFromConfigurationWhenDifferentFromLocalhost() throws Exception {
        String expectedDefaultDomain = "myDomain.org";
        DomainList domainList = testee(DomainListConfiguration.builder()
            .autoDetect(true)
            .autoDetectIp(true)
            .defaultDomain(Domain.of(expectedDefaultDomain))
            .build());

        assertThat(domainList.getDefaultDomain()).isEqualTo(Domain.of(expectedDefaultDomain));
    }

    @Test
    void setDefaultDomainShouldSetFromHostnameWhenEqualsToLocalhost() throws Exception {
        DomainList domainList = testee(DomainListConfiguration.builder()
            .autoDetect(true)
            .autoDetectIp(true)
            .defaultDomain(Domain.LOCALHOST)
            .build());
        Domain expectedDefaultDomain = Domain.of(InetAddress.getLocalHost().getHostName());

        assertThat(domainList.getDefaultDomain()).isEqualTo(expectedDefaultDomain);
    }

    @Test
    void setDefaultDomainShouldCreateFromHostnameWhenEqualsToLocalhost() throws Exception {
        Domain expectedDefaultDomain = Domain.of(InetAddress.getLocalHost().getHostName());
        DomainList domainList = testee(DomainListConfiguration.builder()
            .autoDetect(true)
            .autoDetectIp(true)
            .defaultDomain(expectedDefaultDomain)
            .build());

        assertThat(domainList.getDomains()).contains(expectedDefaultDomain);
    }

    @Test
    void setDefaultDomainShouldNotCreateTwiceWhenCallingTwoTimes() throws Exception {
        Domain expectedDefaultDomain = Domain.of(InetAddress.getLocalHost().getHostName());
        MyDomainList myDomainList = new MyDomainList();
        DomainListConfiguration configuration = DomainListConfiguration.builder()
            .autoDetect(true)
            .autoDetectIp(true)
            .defaultDomain(expectedDefaultDomain)
            .build();
        DomainList domainList = testee(myDomainList, configuration);

        myDomainList.configure(configuration);

        assertThat(domainList.getDomains()).containsOnlyOnce(expectedDefaultDomain);
    }

    @Test
    void setDefaultDomainShouldAddDomainWhenNotContained() throws Exception {
        Domain expectedDefaultDomain = Domain.of("myDomain.org");
        DomainList domainList = testee(DomainListConfiguration.builder()
            .autoDetect(true)
            .autoDetectIp(true)
            .defaultDomain(expectedDefaultDomain)
            .build());

        assertThat(domainList.getDomains()).contains(expectedDefaultDomain);
    }

    @Test
    void setDefaultDomainShouldNotFailWhenDomainContained() throws Exception {
        Domain expectedDefaultDomain = Domain.of("myDomain.org");
        DomainList domainList = testee(DomainListConfiguration.builder()
            .autoDetect(true)
            .autoDetectIp(true)
            .defaultDomain(expectedDefaultDomain)
            .build());

        assertThat(domainList.getDomains()).contains(expectedDefaultDomain);
    }

    @Test
    void getDomainsShouldNotDetectDomainsWhenDisabled() throws Exception {
        Domain domain = Domain.of("domain.tld");
        DomainList domainList = testee(DomainListConfiguration.builder()
            .autoDetect(false)
            .autoDetectIp(false)
            .defaultDomain(Domain.of("domain.tld"))
            .build());

        assertThat(domainList.getDomains()).containsOnly(domain);
    }

    @Test
    void getDomainsShouldNotInteractWithDNSWhenDisabled() throws Exception {
        DomainList domainList = testee(DomainListConfiguration.builder()
            .autoDetect(false)
            .autoDetectIp(false)
            .build());
        domainList.getDomains();

        verifyNoMoreInteractions(dnsService);
    }

    @Test
    void getDomainsShouldContainDetectedDomains() throws Exception {
        DomainList domainList = testee(DomainListConfiguration.builder()
            .autoDetect(true)
            .autoDetectIp(false)
            .build());

        String detected = "detected.tld";
        when(dnsService.getLocalHost()).thenReturn(InetAddress.getByName("127.0.0.1"));
        when(dnsService.getHostName(any(InetAddress.class))).thenReturn(detected);

        assertThat(domainList.getDomains()).contains(Domain.of(detected));
    }

    @Test
    void getDomainsShouldContainDetectedDomainsAndIps() throws Exception {
        DomainList domainList = testee(DomainListConfiguration.builder()
            .autoDetect(true)
            .autoDetectIp(true)
            .build());

        String detected = "detected.tld";
        String detectedIp = "148.25.32.1";
        when(dnsService.getLocalHost()).thenReturn(InetAddress.getByName("127.0.0.1"));
        when(dnsService.getHostName(any(InetAddress.class))).thenReturn(detected);
        InetAddress detectedAddress = mock(InetAddress.class);
        when(detectedAddress.getHostAddress()).thenReturn(detectedIp);
        when(dnsService.getAllByName(detected)).thenReturn(ImmutableList.of(detectedAddress));

        assertThat(domainList.getDomains()).contains(Domain.of(detected), Domain.of(detectedIp));
    }

    @Test
    void getDomainsShouldContainDetectedDomainsAndIpsOfAddedDomains() throws Exception {
        DomainList domainList = testee(DomainListConfiguration.builder()
            .autoDetect(true)
            .autoDetectIp(true)
            .build());

        String added = "added.tld";
        String detected = "detected.tld";
        String detectedIp1 = "148.25.32.1";
        String detectedIp2 = "148.25.32.2";
        when(dnsService.getLocalHost()).thenReturn(InetAddress.getByName("127.0.0.1"));
        when(dnsService.getHostName(any(InetAddress.class))).thenReturn(detected);
        InetAddress detectedAddress1 = mock(InetAddress.class);
        InetAddress detectedAddress2 = mock(InetAddress.class);
        when(detectedAddress1.getHostAddress()).thenReturn(detectedIp1);
        when(detectedAddress2.getHostAddress()).thenReturn(detectedIp2);
        when(dnsService.getAllByName(detected)).thenReturn(ImmutableList.of(detectedAddress1));
        when(dnsService.getAllByName(added)).thenReturn(ImmutableList.of(detectedAddress2));
        domainList.addDomain(Domain.of(added));

        assertThat(domainList.getDomains())
            .extracting(Domain::name)
            .contains(detected, detectedIp1, added, detectedIp2);
    }

    @Test
    void getDomainsShouldNotReturnDuplicates() throws Exception {
        DomainList domainList = testee(DomainListConfiguration.builder()
            .autoDetect(true)
            .autoDetectIp(true));

        String added = "added.tld";
        String detected = "detected.tld";
        String ip = "148.25.32.1";

        when(dnsService.getLocalHost()).thenReturn(InetAddress.getByName("127.0.0.1"));
        when(dnsService.getHostName(any(InetAddress.class))).thenReturn(detected);
        InetAddress address = mock(InetAddress.class);
        when(address.getHostAddress()).thenReturn(ip);
        when(dnsService.getAllByName(any())).thenReturn(ImmutableList.of(address));

        domainList.addDomain(Domain.of(added));
        domainList.addDomain(Domain.of(ip));

        assertThat(domainList.getDomains())
            .extracting(Domain::name)
            .containsOnlyOnce(added, detected, ip);
    }

    @Test
    void getDomainsShouldListAddedDomain() throws Exception {
        Domain defaultDomain = Domain.of("default.tld");
        Domain domain = Domain.of("added.tld");

        MyDomainList myDomainList = new MyDomainList();
        myDomainList.addDomain(domain);

        DomainList domainList = testee(myDomainList, DomainListConfiguration.builder()
            .autoDetect(false)
            .autoDetectIp(false)
            .defaultDomain(defaultDomain)
            .build());

        assertThat(domainList.getDomains()).containsOnly(domain, defaultDomain);
    }

    @Test
    void containsDomainShouldReturnDetectedIp() throws Exception {
        DomainList domainList = testee(DomainListConfiguration.builder()
            .autoDetect(true)
            .autoDetectIp(true)
            .build());

        String detected = "detected.tld";
        String detectedIp = "148.25.32.1";
        when(dnsService.getLocalHost()).thenReturn(InetAddress.getByName("127.0.0.1"));
        when(dnsService.getHostName(any(InetAddress.class))).thenReturn(detected);
        InetAddress detectedAddress = mock(InetAddress.class);
        when(detectedAddress.getHostAddress()).thenReturn(detectedIp);
        when(dnsService.getAllByName(detected)).thenReturn(ImmutableList.of(detectedAddress));

        assertThat(domainList.containsDomain(Domain.of(detectedIp))).isTrue();
    }

    @Test
    void containsDomainShouldReturnTrueWhenDomainIsContained() throws Exception {
        Domain domain = Domain.of("added.tld");
        MyDomainList myDomainList = new MyDomainList();
        myDomainList.addDomain(domain);
        DomainList domainList = testee(myDomainList, DomainListConfiguration.builder()
            .autoDetect(false)
            .autoDetectIp(false)
            .build());

        assertThat(domainList.containsDomain(domain)).isTrue();
    }

    @Test
    void containsDomainShouldReturnFalseWhenDomainIsNotContained() throws Exception {
        Domain domain = Domain.of("added.tld");

        DomainList domainList = testee(DomainListConfiguration.builder()
            .autoDetect(false)
            .autoDetectIp(false));

        assertThat(domainList.containsDomain(domain)).isFalse();
    }

    @Test
    void containsDomainShouldNotInteractWithDNSWhenDisabled() throws Exception {
        DomainList domainList = testee(DomainListConfiguration.builder()
            .autoDetect(false)
            .autoDetectIp(false));
        domainList.containsDomain(Domain.of("added.tld"));

        verifyNoMoreInteractions(dnsService);
    }

    @Test
    void containsDomainShouldReturnDetectedDomains() throws Exception {
        DomainList domainList = testee(DomainListConfiguration.builder()
            .autoDetect(true)
            .autoDetectIp(false));

        String detected = "detected.tld";
        when(dnsService.getLocalHost()).thenReturn(InetAddress.getByName("127.0.0.1"));
        when(dnsService.getHostName(any(InetAddress.class))).thenReturn(detected);

        assertThat(domainList.containsDomain(Domain.of(detected))).isTrue();
    }

    @Test
    void envDomainShouldBeAddedUponConfiguration() throws Exception {
        String envDomain = "env.tld";
        when(envDetector.getEnv(DomainListConfiguration.ENV_DOMAIN)).thenReturn(envDomain);

        DomainList domainList = testee(DomainListConfiguration.builder()
            .autoDetect(true)
            .autoDetectIp(false)
            .addDomainFromEnv(envDetector));

        assertThat(domainList.containsDomain(Domain.of(envDomain))).isTrue();
    }

    @Test
    void envDomainShouldNotFailWhenDomainExists() throws Exception {
        String envDomain = "env.tld";
        MyDomainList myDomainList = new MyDomainList();
        myDomainList.addDomain(Domain.of(envDomain));
        when(envDetector.getEnv(DomainListConfiguration.ENV_DOMAIN)).thenReturn(envDomain);

        DomainList domainList = testee(myDomainList, DomainListConfiguration.builder()
            .autoDetect(true)
            .autoDetectIp(false)
            .addDomainFromEnv(envDetector)
            .build());

        assertThat(domainList.containsDomain(Domain.of(envDomain))).isTrue();
    }

    @Test
    void removeDomainShouldThrowWhenRemovingAutoDetectedDomains() throws Exception {
        DomainList domainList = testee(DomainListConfiguration.builder()
            .autoDetect(true)
            .autoDetectIp(false));

        String detected = "detected.tld";
        when(dnsService.getLocalHost()).thenReturn(InetAddress.getByName("127.0.0.1"));
        when(dnsService.getHostName(any(InetAddress.class))).thenReturn(detected);

        assertThatThrownBy(() -> domainList.removeDomain(Domain.of(detected)))
            .isInstanceOf(AutoDetectedDomainRemovalException.class);
    }

    @Test
    void removeDomainShouldThrowWhenRemovingAutoDetectedIps() throws Exception {
        DomainList domainList = testee(DomainListConfiguration.builder()
            .autoDetect(true)
            .autoDetectIp(true)
            .build());

        String detected = "detected.tld";
        String detectedIp = "148.25.32.1";
        when(dnsService.getLocalHost()).thenReturn(InetAddress.getByName("127.0.0.1"));
        when(dnsService.getHostName(any(InetAddress.class))).thenReturn(detected);
        InetAddress detectedAddress = mock(InetAddress.class);
        when(detectedAddress.getHostAddress()).thenReturn(detectedIp);
        when(dnsService.getAllByName(detected)).thenReturn(ImmutableList.of(detectedAddress));

        assertThatThrownBy(() -> domainList.removeDomain(Domain.of(detectedIp)))
            .isInstanceOf(AutoDetectedDomainRemovalException.class);
    }

    @Test
    void removeDomainShouldThrowWhenRemovingDefaultDomain() throws Exception {
        Domain defaultDomain = Domain.of("default.tld");
        DomainList domainList = testee(DomainListConfiguration.builder()
            .autoDetect(true)
            .autoDetectIp(true)
            .defaultDomain(defaultDomain)
            .build());

        assertThatThrownBy(() -> domainList.removeDomain(defaultDomain))
            .isInstanceOf(AutoDetectedDomainRemovalException.class);
    }

    @Test
    void configuredDomainShouldBeAddedUponConfiguration() throws Exception {
        Domain domain1 = Domain.of("conf1.tld");
        Domain domain2 = Domain.of("conf2.tld");

        DomainList domainList = testee(DomainListConfiguration.builder()
            .autoDetect(false)
            .autoDetectIp(false)
            .addConfiguredDomains(domain1, domain2));

        assertThat(domainList.getDomains()).contains(domain1, domain2);
    }

    @Test
    void configureShouldNotAttemptToChangeLocalHostDefaultDomainWhenNoAutoDetect() throws Exception {
        DomainList domainList = testee(DomainListConfiguration.builder()
            .autoDetect(false)
            .autoDetectIp(false)
            .defaultDomain(Domain.LOCALHOST));

        assertThat(domainList.getDefaultDomain()).isEqualTo(Domain.LOCALHOST);
    }

}
