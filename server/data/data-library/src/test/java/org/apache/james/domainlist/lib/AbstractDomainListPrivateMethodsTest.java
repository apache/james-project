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
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.api.DomainListException;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class AbstractDomainListPrivateMethodsTest {

    public static final Logger LOGGER = LoggerFactory.getLogger(AbstractDomainListPrivateMethodsTest.class);
    private MyDomainList domainList;
    private DNSService dnsService;
    private EnvDetector envDetector;

    @Before
    public void setup() {
        dnsService = mock(DNSService.class);
        envDetector = mock(EnvDetector.class);
        domainList = new MyDomainList(dnsService, envDetector);
        domainList.setLog(LOGGER);
    }

    private static class MyDomainList extends AbstractDomainList {

        private List<String> domains;

        public MyDomainList(DNSService dns, EnvDetector envDetector) {
            super(dns, envDetector);
            this.domains = Lists.newArrayList();
        }

        @Override
        protected boolean containsDomainInternal(String domain) throws DomainListException {
            return domains.contains(domain);
        }

        @Override
        public void addDomain(String domain) throws DomainListException {
            domains.add(domain);
        }

        @Override
        public void removeDomain(String domain) throws DomainListException {
            domains.remove(domain);
        }

        @Override
        protected List<String> getDomainListInternal() throws DomainListException {
            return domains;
        }
    }

    @Test
    public void setDefaultDomainShouldSetFromConfigurationWhenDifferentFromLocalhost() throws Exception {
        HierarchicalConfiguration configuration = mock(HierarchicalConfiguration.class);
        String expectedDefaultDomain = "myDomain.org";
        when(configuration.getString(AbstractDomainList.CONFIGURE_DEFAULT_DOMAIN, AbstractDomainList.LOCALHOST))
            .thenReturn(expectedDefaultDomain);

        domainList.configureDefaultDomain(configuration);

        assertThat(domainList.getDefaultDomain()).isEqualTo(expectedDefaultDomain);
    }

    @Test
    public void setDefaultDomainShouldSetFromHostnameWhenEqualsToLocalhost() throws Exception {
        HierarchicalConfiguration configuration = mock(HierarchicalConfiguration.class);
        when(configuration.getString(AbstractDomainList.CONFIGURE_DEFAULT_DOMAIN, AbstractDomainList.LOCALHOST))
            .thenReturn(AbstractDomainList.LOCALHOST);

        String expectedDefaultDomain = InetAddress.getLocalHost().getHostName();
        domainList.configureDefaultDomain(configuration);

        assertThat(domainList.getDefaultDomain()).isEqualTo(expectedDefaultDomain);
    }

    @Test
    public void setDefaultDomainShouldCreateFromHostnameWhenEqualsToLocalhost() throws Exception {
        HierarchicalConfiguration configuration = mock(HierarchicalConfiguration.class);
        when(configuration.getString(AbstractDomainList.CONFIGURE_DEFAULT_DOMAIN, AbstractDomainList.LOCALHOST))
            .thenReturn(AbstractDomainList.LOCALHOST);

        String expectedDefaultDomain = InetAddress.getLocalHost().getHostName();
        domainList.configureDefaultDomain(configuration);

        assertThat(domainList.getDomainListInternal()).contains(expectedDefaultDomain);
    }

    @Test
    public void setDefaultDomainShouldNotCreateTwiceWhenCallingTwoTimes() throws Exception {
        HierarchicalConfiguration configuration = mock(HierarchicalConfiguration.class);
        when(configuration.getString(AbstractDomainList.CONFIGURE_DEFAULT_DOMAIN, AbstractDomainList.LOCALHOST))
            .thenReturn(AbstractDomainList.LOCALHOST);

        String expectedDefaultDomain = InetAddress.getLocalHost().getHostName();
        domainList.configureDefaultDomain(configuration);
        domainList.configureDefaultDomain(configuration);

        assertThat(domainList.getDomainListInternal()).containsOnlyOnce(expectedDefaultDomain);
    }

    @Test
    public void setDefaultDomainShouldAddDomainWhenNotContained() throws Exception {
        HierarchicalConfiguration configuration = mock(HierarchicalConfiguration.class);
        String expectedDefaultDomain = "myDomain.org";
        when(configuration.getString(AbstractDomainList.CONFIGURE_DEFAULT_DOMAIN, AbstractDomainList.LOCALHOST))
            .thenReturn(expectedDefaultDomain);

        domainList.configureDefaultDomain(configuration);

        assertThat(domainList.getDomainListInternal()).contains(expectedDefaultDomain);
    }

    @Test
    public void setDefaultDomainShouldNotFailWhenDomainContained() throws Exception {
        HierarchicalConfiguration configuration = mock(HierarchicalConfiguration.class);
        String expectedDefaultDomain = "myDomain.org";
        when(configuration.getString(AbstractDomainList.CONFIGURE_DEFAULT_DOMAIN, AbstractDomainList.LOCALHOST))
            .thenReturn(expectedDefaultDomain);

        domainList.addDomain(expectedDefaultDomain);
        domainList.configureDefaultDomain(configuration);

        assertThat(domainList.getDomainListInternal()).contains(expectedDefaultDomain);
    }

    @Test
    public void getDomainsShouldNotDetectDomainsWhenDisabled () throws Exception {
        HierarchicalConfiguration configuration = mock(HierarchicalConfiguration.class);

        when(configuration.getBoolean(AbstractDomainList.CONFIGURE_AUTODETECT, true)).thenReturn(false);
        when(configuration.getBoolean(AbstractDomainList.CONFIGURE_AUTODETECT_IP, true)).thenReturn(false);
        domainList.configure(configuration);

        assertThat(domainList.getDomains()).isEmpty();
    }

    @Test
    public void getDomainsShouldNotInteractWithDNSWhenDisabled () throws Exception {
        HierarchicalConfiguration configuration = mock(HierarchicalConfiguration.class);

        when(configuration.getBoolean(AbstractDomainList.CONFIGURE_AUTODETECT, true)).thenReturn(false);
        when(configuration.getBoolean(AbstractDomainList.CONFIGURE_AUTODETECT_IP, true)).thenReturn(false);
        domainList.configure(configuration);
        domainList.getDomains();

        verifyZeroInteractions(dnsService);
    }

    @Test
    public void getDomainsShouldContainDetectedDomains() throws Exception {
        HierarchicalConfiguration configuration = mock(HierarchicalConfiguration.class);

        when(configuration.getBoolean(AbstractDomainList.CONFIGURE_AUTODETECT, true)).thenReturn(true);
        when(configuration.getBoolean(AbstractDomainList.CONFIGURE_AUTODETECT_IP, true)).thenReturn(false);
        domainList.setLog(LOGGER);
        domainList.configure(configuration);

        String detected = "detected.tld";
        when(dnsService.getHostName(any(InetAddress.class))).thenReturn(detected);

        assertThat(domainList.getDomains()).containsOnly(detected);
    }

    @Test
    public void getDomainsShouldContainDetectedDomainsAndIps() throws Exception {
        HierarchicalConfiguration configuration = mock(HierarchicalConfiguration.class);

        when(configuration.getBoolean(AbstractDomainList.CONFIGURE_AUTODETECT, true)).thenReturn(true);
        when(configuration.getBoolean(AbstractDomainList.CONFIGURE_AUTODETECT_IP, true)).thenReturn(true);
        domainList.setLog(LOGGER);
        domainList.configure(configuration);

        String detected = "detected.tld";
        String detectedIp = "148.25.32.1";
        when(dnsService.getHostName(any(InetAddress.class))).thenReturn(detected);
        InetAddress detectedAddress = mock(InetAddress.class);
        when(detectedAddress.getHostAddress()).thenReturn(detectedIp);
        when(dnsService.getAllByName(detected)).thenReturn(ImmutableList.of(detectedAddress));

        assertThat(domainList.getDomains()).containsOnly(detected, detectedIp);
    }

    @Test
    public void getDomainsShouldContainDetectedDomainsAndIpsOfAddedDomains() throws Exception {
        HierarchicalConfiguration configuration = mock(HierarchicalConfiguration.class);

        when(configuration.getBoolean(AbstractDomainList.CONFIGURE_AUTODETECT, true)).thenReturn(true);
        when(configuration.getBoolean(AbstractDomainList.CONFIGURE_AUTODETECT_IP, true)).thenReturn(true);
        domainList.setLog(LOGGER);
        domainList.configure(configuration);

        String added = "added.tld";
        String detected = "detected.tld";
        String detectedIp1 = "148.25.32.1";
        String detectedIp2 = "148.25.32.2";
        when(dnsService.getHostName(any(InetAddress.class))).thenReturn(detected);
        InetAddress detectedAddress1 = mock(InetAddress.class);
        InetAddress detectedAddress2 = mock(InetAddress.class);
        when(detectedAddress1.getHostAddress()).thenReturn(detectedIp1);
        when(detectedAddress2.getHostAddress()).thenReturn(detectedIp2);
        when(dnsService.getAllByName(detected)).thenReturn(ImmutableList.of(detectedAddress1));
        when(dnsService.getAllByName(added)).thenReturn(ImmutableList.of(detectedAddress2));
        domainList.addDomain(added);

        assertThat(domainList.getDomains()).containsOnly(detected, detectedIp1, added, detectedIp2);
    }

    @Test
    public void getDomainsShouldListAddedDomain() throws Exception {
        HierarchicalConfiguration configuration = mock(HierarchicalConfiguration.class);

        when(configuration.getBoolean(AbstractDomainList.CONFIGURE_AUTODETECT, true)).thenReturn(false);
        when(configuration.getBoolean(AbstractDomainList.CONFIGURE_AUTODETECT_IP, true)).thenReturn(false);
        String domain = "added.tld";
        domainList.addDomain(domain);
        domainList.configure(configuration);

        assertThat(domainList.getDomains()).containsOnly(domain);
    }

    @Test
    public void containsDomainShouldReturnDetectedIp() throws Exception {
        String detected = "detected.tld";
        String detectedIp = "148.25.32.1";
        when(dnsService.getHostName(any(InetAddress.class))).thenReturn(detected);
        InetAddress detectedAddress = mock(InetAddress.class);
        when(detectedAddress.getHostAddress()).thenReturn(detectedIp);
        when(dnsService.getAllByName(detected)).thenReturn(ImmutableList.of(detectedAddress));

        assertThat(domainList.containsDomain(detectedIp)).isTrue();
    }

    @Test
    public void containsDomainShouldReturnTrueWhenDomainIsContained() throws Exception {
        HierarchicalConfiguration configuration = mock(HierarchicalConfiguration.class);

        when(configuration.getBoolean(AbstractDomainList.CONFIGURE_AUTODETECT, true)).thenReturn(false);
        when(configuration.getBoolean(AbstractDomainList.CONFIGURE_AUTODETECT_IP, true)).thenReturn(false);
        String domain = "added.tld";
        domainList.addDomain(domain);
        domainList.configure(configuration);

        assertThat(domainList.containsDomain(domain)).isTrue();
    }

    @Test
    public void containsDomainShouldReturnFalseWhenDomainIsNotContained() throws Exception {
        HierarchicalConfiguration configuration = mock(HierarchicalConfiguration.class);

        when(configuration.getBoolean(AbstractDomainList.CONFIGURE_AUTODETECT, true)).thenReturn(false);
        when(configuration.getBoolean(AbstractDomainList.CONFIGURE_AUTODETECT_IP, true)).thenReturn(false);
        String domain = "added.tld";
        domainList.configure(configuration);

        assertThat(domainList.containsDomain(domain)).isFalse();
    }

    @Test
    public void containsDomainShouldNotInteractWithDNSWhenDisabled () throws Exception {
        HierarchicalConfiguration configuration = mock(HierarchicalConfiguration.class);

        when(configuration.getBoolean(AbstractDomainList.CONFIGURE_AUTODETECT, true)).thenReturn(false);
        when(configuration.getBoolean(AbstractDomainList.CONFIGURE_AUTODETECT_IP, true)).thenReturn(false);
        domainList.configure(configuration);
        domainList.containsDomain("added.tld");

        verifyZeroInteractions(dnsService);
    }

    @Test
    public void containsDomainShouldReturnDetectedDomains() throws Exception {
        HierarchicalConfiguration configuration = mock(HierarchicalConfiguration.class);

        when(configuration.getBoolean(AbstractDomainList.CONFIGURE_AUTODETECT, true)).thenReturn(true);
        when(configuration.getBoolean(AbstractDomainList.CONFIGURE_AUTODETECT_IP, true)).thenReturn(false);
        domainList.configure(configuration);

        String detected = "detected.tld";
        when(dnsService.getHostName(any(InetAddress.class))).thenReturn(detected);

        assertThat(domainList.containsDomain(detected)).isTrue();
    }

    @Test
    public void envDomainShouldBeAddedUponConfiguration() throws Exception {
        String envDomain = "env.tld";
        when(envDetector.getEnv(AbstractDomainList.ENV_DOMAIN)).thenReturn(envDomain);

        HierarchicalConfiguration configuration = mock(HierarchicalConfiguration.class);
        when(configuration.getBoolean(AbstractDomainList.CONFIGURE_AUTODETECT, true)).thenReturn(true);
        when(configuration.getBoolean(AbstractDomainList.CONFIGURE_AUTODETECT_IP, true)).thenReturn(false);
        domainList.configure(configuration);

        assertThat(domainList.containsDomain(envDomain)).isTrue();
    }

    @Test
    public void configuredDomainShouldBeAddedUponConfiguration() throws Exception {
        String[] configuredDomain = new String[] {"conf1.tld", "conf2.tld"};

        HierarchicalConfiguration configuration = mock(HierarchicalConfiguration.class);
        when(configuration.getBoolean(AbstractDomainList.CONFIGURE_AUTODETECT, true)).thenReturn(true);
        when(configuration.getBoolean(AbstractDomainList.CONFIGURE_AUTODETECT_IP, true)).thenReturn(false);
        when(configuration.getStringArray(AbstractDomainList.CONFIGURE_DOMAIN_NAMES)).thenReturn(configuredDomain);
        domainList.configure(configuration);

        assertThat(domainList.getDomains())
            .containsOnlyElementsOf(Arrays.asList(configuredDomain));
    }

}
