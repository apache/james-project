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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.util.List;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.domainlist.api.DomainListException;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Lists;

public class AbstractDomainListPrivateMethodsTest {

    private MyDomainList domainList;
    
    @Before
    public void setup() {
        domainList = new MyDomainList();
    }

    private static class MyDomainList extends AbstractDomainList {

        private List<String> domains;

        public MyDomainList() {
            domains = Lists.newArrayList();
        }

        @Override
        public boolean containsDomain(String domain) throws DomainListException {
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
        when(configuration.getString("defaultDomain", AbstractDomainList.LOCALHOST))
            .thenReturn(expectedDefaultDomain);

        domainList.configureDefaultDomain(configuration);

        assertThat(domainList.getDefaultDomain()).isEqualTo(expectedDefaultDomain);
    }

    @Test
    public void setDefaultDomainShouldSetFromHostnameWhenEqualsToLocalhost() throws Exception {
        HierarchicalConfiguration configuration = mock(HierarchicalConfiguration.class);
        when(configuration.getString("defaultDomain", AbstractDomainList.LOCALHOST))
            .thenReturn(AbstractDomainList.LOCALHOST);

        String expectedDefaultDomain = InetAddress.getLocalHost().getHostName();
        domainList.configureDefaultDomain(configuration);

        assertThat(domainList.getDefaultDomain()).isEqualTo(expectedDefaultDomain);
    }

    @Test
    public void setDefaultDomainShouldCreateFromHostnameWhenEqualsToLocalhost() throws Exception {
        HierarchicalConfiguration configuration = mock(HierarchicalConfiguration.class);
        when(configuration.getString("defaultDomain", AbstractDomainList.LOCALHOST))
            .thenReturn(AbstractDomainList.LOCALHOST);

        String expectedDefaultDomain = InetAddress.getLocalHost().getHostName();
        domainList.configureDefaultDomain(configuration);

        assertThat(domainList.getDomainListInternal()).contains(expectedDefaultDomain);
    }

    @Test
    public void setDefaultDomainShouldNotCreateTwiceWhenCallingTwoTimes() throws Exception {
        HierarchicalConfiguration configuration = mock(HierarchicalConfiguration.class);
        when(configuration.getString("defaultDomain", AbstractDomainList.LOCALHOST))
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
        when(configuration.getString("defaultDomain", AbstractDomainList.LOCALHOST))
            .thenReturn(expectedDefaultDomain);

        domainList.configureDefaultDomain(configuration);

        assertThat(domainList.getDomainListInternal()).contains(expectedDefaultDomain);
    }

    @Test
    public void setDefaultDomainNotFailWhenDomainContained() throws Exception {
        HierarchicalConfiguration configuration = mock(HierarchicalConfiguration.class);
        String expectedDefaultDomain = "myDomain.org";
        when(configuration.getString("defaultDomain", AbstractDomainList.LOCALHOST))
            .thenReturn(expectedDefaultDomain);

        domainList.addDomain(expectedDefaultDomain);
        domainList.configureDefaultDomain(configuration);

        assertThat(domainList.getDomainListInternal()).contains(expectedDefaultDomain);
    }
}
