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
package org.apache.james.http.jetty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.server.core.configuration.FileConfigurationProvider;
import org.junit.Test;

import com.google.common.collect.ImmutableMap;

public class JettyHttpServerFactoryTest {

    @Test
    public void shouldCreateServersAsDescribedInXmlConfiguration() throws Exception {
        HierarchicalConfiguration<ImmutableNode> configuration = FileConfigurationProvider.getConfig(ClassLoader.getSystemResourceAsStream("httpserver.xml"));
        List<JettyHttpServer> servers = new JettyHttpServerFactory().createServers(configuration);
        assertThat(servers).extracting(JettyHttpServer::getConfiguration)
            .containsOnly(Configuration.builder()
                        .port(5000)
                        .serve("/foo")
                        .with(Ok200.class)
                        .serve("/bar")
                        .with(Bad400.class)
                        .build(),
                    Configuration.builder()
                        .randomPort()
                        .serve("/foo")
                        .with(Ok200.class)
                        .filter("/*")
                        .with(SpyFilter.class).only()
                    .build());
    }

    @Test
    public void shouldThrowOnEmptyServletName() throws Exception {
        HierarchicalConfiguration<ImmutableNode> configuration = FileConfigurationProvider.getConfig(ClassLoader.getSystemResourceAsStream("emptyservletname.xml"));
        assertThatThrownBy(() -> new JettyHttpServerFactory().createServers(configuration)).isInstanceOf(ConfigurationException.class);
    }

    @Test
    public void shouldThrowOnUnavailableServletName() throws Exception {
        HierarchicalConfiguration<ImmutableNode> configuration = FileConfigurationProvider.getConfig(ClassLoader.getSystemResourceAsStream("unavailableservletname.xml"));
        assertThatThrownBy(() -> new JettyHttpServerFactory().createServers(configuration)).isInstanceOf(ConfigurationException.class);
    }
    
    @Test
    public void shouldThrowOnConflictingPortConfiguration() throws Exception {
        HierarchicalConfiguration<ImmutableNode> configuration = FileConfigurationProvider.getConfig(ClassLoader.getSystemResourceAsStream("conflictingport.xml"));
        assertThatThrownBy(() -> new JettyHttpServerFactory().createServers(configuration)).isInstanceOf(ConfigurationException.class);
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void shouldBeAbleToLoadEmptyMappingConfiguration() throws Exception {
        HierarchicalConfiguration<ImmutableNode> configuration = FileConfigurationProvider.getConfig(ClassLoader.getSystemResourceAsStream("emptymappingconfiguration.xml"));
        assertThat(new JettyHttpServerFactory().createServers(configuration))
            .extracting(server -> server.getConfiguration().getMappings())
            .containsOnly(ImmutableMap.of());
    }

    @Test
    public void shouldThrowOnEmptyFilterName() throws Exception {
        HierarchicalConfiguration<ImmutableNode> configuration = FileConfigurationProvider.getConfig(ClassLoader.getSystemResourceAsStream("emptyfiltername.xml"));
        assertThatThrownBy(() -> new JettyHttpServerFactory().createServers(configuration)).isInstanceOf(ConfigurationException.class);
    }

    @Test
    public void shouldThrowOnUnavailableFilterName() throws Exception {
        HierarchicalConfiguration<ImmutableNode> configuration = FileConfigurationProvider.getConfig(ClassLoader.getSystemResourceAsStream("unavailablefiltername.xml"));
        assertThatThrownBy(() -> new JettyHttpServerFactory().createServers(configuration)).isInstanceOf(ConfigurationException.class);
    }
    
}
