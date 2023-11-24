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
import org.apache.james.domainlist.api.DomainListException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

class AbstractDomainListPrivateMethodsTest {
    MyDomainList domainList;
    DNSService dnsService;
    EnvDetector envDetector;

    @BeforeEach
    void setup() {
        dnsService = mock(DNSService.class);
        envDetector = mock(EnvDetector.class);
        domainList = new MyDomainList(dnsService);
    }

    private static class MyDomainList extends AbstractDomainList {

        private List<Domain> domains;

        MyDomainList(DNSService dns) {
            super(dns);
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
        domainList.configure(DomainListConfiguration.builder()
            .autoDetect(true)
            .autoDetectIp(true)
            .build());
        String expectedDefaultDomain = "myDomain.org";

        domainList.configureDefaultDomain(Domain.of(expectedDefaultDomain));

        assertThat(domainList.getDefaultDomain()).isEqualTo(Domain.of(expectedDefaultDomain));
    }

    @Test
    void setDefaultDomainShouldSetFromHostnameWhenEqualsToLocalhost() throws Exception {
        domainList.configure(DomainListConfiguration.builder()
            .autoDetect(true)
            .autoDetectIp(true)
            .build());
        Domain expectedDefaultDomain = Domain.of(InetAddress.getLocalHost().getHostName());
        domainList.configureDefaultDomain(Domain.LOCALHOST);

        assertThat(domainList.getDefaultDomain()).isEqualTo(expectedDefaultDomain);
    }

    @Test
    void setDefaultDomainShouldCreateFromHostnameWhenEqualsToLocalhost() throws Exception {
        domainList.configure(DomainListConfiguration.builder()
            .autoDetect(true)
            .autoDetectIp(true)
            .build());

        Domain expectedDefaultDomain = Domain.of(InetAddress.getLocalHost().getHostName());
        domainList.configureDefaultDomain(expectedDefaultDomain);

        assertThat(domainList.getDomainListInternal()).contains(expectedDefaultDomain);
    }

    @Test
    void setDefaultDomainShouldNotCreateTwiceWhenCallingTwoTimes() throws Exception {
        domainList.configure(DomainListConfiguration.builder()
            .autoDetect(true)
            .autoDetectIp(true)
            .build());

        Domain expectedDefaultDomain = Domain.of(InetAddress.getLocalHost().getHostName());
        domainList.configureDefaultDomain(expectedDefaultDomain);
        domainList.configureDefaultDomain(expectedDefaultDomain);

        assertThat(domainList.getDomainListInternal()).containsOnlyOnce(expectedDefaultDomain);
    }

    @Test
    void setDefaultDomainShouldAddDomainWhenNotContained() throws Exception {
        domainList.configure(DomainListConfiguration.builder()
            .autoDetect(true)
            .autoDetectIp(true)
            .build());

        Domain expectedDefaultDomain = Domain.of("myDomain.org");

        domainList.configureDefaultDomain(expectedDefaultDomain);

        assertThat(domainList.getDomainListInternal()).contains(expectedDefaultDomain);
    }

    @Test
    void setDefaultDomainShouldNotFailWhenDomainContained() throws Exception {
        domainList.configure(DomainListConfiguration.builder()
            .autoDetect(true)
            .autoDetectIp(true)
            .build());

        Domain expectedDefaultDomain = Domain.of("myDomain.org");

        domainList.addDomain(expectedDefaultDomain);
        domainList.configureDefaultDomain(expectedDefaultDomain);

        assertThat(domainList.getDomainListInternal()).contains(expectedDefaultDomain);
    }

    @Test
    void getDomainsShouldNotDetectDomainsWhenDisabled() throws Exception {
        Domain domain = Domain.of("domain.tld");
        domainList.configure(DomainListConfiguration.builder()
            .autoDetect(false)
            .autoDetectIp(false)
            .defaultDomain(domain));

        assertThat(domainList.getDomains()).containsOnly(domain);
    }

    @Test
    void getDomainsShouldNotInteractWithDNSWhenDisabled() throws Exception {
        domainList.configure(DomainListConfiguration.builder()
            .autoDetect(false)
            .autoDetectIp(false));
        domainList.getDomains();

        verifyNoMoreInteractions(dnsService);
    }

    @Test
    void getDomainsShouldContainDetectedDomains() throws Exception {
        domainList.configure(DomainListConfiguration.builder()
            .autoDetect(true)
            .autoDetectIp(false));

        String detected = "detected.tld";
        when(dnsService.getLocalHost()).thenReturn(InetAddress.getByName("127.0.0.1"));
        when(dnsService.getHostName(any(InetAddress.class))).thenReturn(detected);

        assertThat(domainList.getDomains()).contains(Domain.of(detected));
    }

    @Test
    void getDomainsShouldContainDetectedDomainsAndIps() throws Exception {
        domainList.configure(DomainListConfiguration.builder()
            .autoDetect(true)
            .autoDetectIp(true));

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
        domainList.configure(DomainListConfiguration.builder()
            .autoDetect(true)
            .autoDetectIp(true));

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
        domainList.configure(DomainListConfiguration.builder()
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

        domainList.addDomain(domain);

        domainList.configure(DomainListConfiguration.builder()
            .autoDetect(false)
            .autoDetectIp(false)
            .defaultDomain(defaultDomain));

        assertThat(domainList.getDomains()).containsOnly(domain, defaultDomain);
    }

    @Test
    void containsDomainShouldReturnDetectedIp() throws Exception {
        domainList.configure(DomainListConfiguration.builder()
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
        domainList.addDomain(domain);
        domainList.configure(DomainListConfiguration.builder()
            .autoDetect(false)
            .autoDetectIp(false));

        assertThat(domainList.containsDomain(domain)).isTrue();
    }

    @Test
    void containsDomainShouldReturnFalseWhenDomainIsNotContained() throws Exception {
        Domain domain = Domain.of("added.tld");

        domainList.configure(DomainListConfiguration.builder()
            .autoDetect(false)
            .autoDetectIp(false));

        assertThat(domainList.containsDomain(domain)).isFalse();
    }

    @Test
    void containsDomainShouldNotInteractWithDNSWhenDisabled() throws Exception {
        domainList.configure(DomainListConfiguration.builder()
            .autoDetect(false)
            .autoDetectIp(false));
        domainList.containsDomain(Domain.of("added.tld"));

        verifyNoMoreInteractions(dnsService);
    }

    @Test
    void containsDomainShouldReturnDetectedDomains() throws Exception {
        domainList.configure(DomainListConfiguration.builder()
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

        domainList.configure(DomainListConfiguration.builder()
            .autoDetect(true)
            .autoDetectIp(false)
            .addDomainFromEnv(envDetector));

        assertThat(domainList.containsDomain(Domain.of(envDomain))).isTrue();
    }

    @Test
    void envDomainShouldNotFailWhenDomainExists() throws Exception {
        String envDomain = "env.tld";
        domainList.addDomain(Domain.of(envDomain));
        when(envDetector.getEnv(DomainListConfiguration.ENV_DOMAIN)).thenReturn(envDomain);


        domainList.configure(DomainListConfiguration.builder()
            .autoDetect(true)
            .autoDetectIp(false)
            .addDomainFromEnv(envDetector));

        assertThat(domainList.containsDomain(Domain.of(envDomain))).isTrue();
    }

    @Test
    void removeDomainShouldThrowWhenRemovingAutoDetectedDomains() throws Exception {
        domainList.configure(DomainListConfiguration.builder()
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
        domainList.configure(DomainListConfiguration.builder()
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
        domainList.configure(DomainListConfiguration.builder()
            .autoDetect(true)
            .autoDetectIp(true)
            .build());

        Domain defaultDomain = Domain.of("default.tld");
        domainList.configureDefaultDomain(defaultDomain);

        assertThatThrownBy(() -> domainList.removeDomain(defaultDomain))
            .isInstanceOf(AutoDetectedDomainRemovalException.class);
    }

    @Test
    void configuredDomainShouldBeAddedUponConfiguration() throws Exception {
        Domain domain1 = Domain.of("conf1.tld");
        Domain domain2 = Domain.of("conf2.tld");

        domainList.configure(DomainListConfiguration.builder()
            .autoDetect(false)
            .autoDetectIp(false)
            .addConfiguredDomains(domain1, domain2));

        assertThat(domainList.getDomains()).contains(domain1, domain2);
    }

    @Test
    void configureShouldNotAttemptToChangeLocalHostDefaultDomainWhenNoAutoDetect() throws Exception {
        domainList.configure(DomainListConfiguration.builder()
            .autoDetect(false)
            .autoDetectIp(false)
            .defaultDomain(Domain.LOCALHOST));

        assertThat(domainList.getDefaultDomain()).isEqualTo(Domain.LOCALHOST);
    }

}
