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

package org.apache.james.modules.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationRuntimeException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.mailetcontainer.impl.CompositeProcessorImpl;
import org.apache.james.mailetcontainer.impl.JamesMailSpooler;
import org.apache.james.modules.server.MailetContainerModule.MailetModuleInitializationOperation;
import org.apache.james.server.core.configuration.ConfigurationProvider;
import org.apache.james.server.core.configuration.FileConfigurationProvider;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableSet;

class MailetContainerModuleTest {

    public static final ImmutableSet<MailetContainerModule.ProcessorsCheck> NO_TRANSPORT_CHECKS = ImmutableSet.of();

    @Test
    void getMailetContextConfigurationShouldReturnEmptyWhenNoContextSection() throws Exception {
        ConfigurationProvider configurationProvider = mock(ConfigurationProvider.class);
        when(configurationProvider.getConfiguration("mailetcontainer"))
            .thenReturn(new BaseHierarchicalConfiguration());

        MailetContainerModule testee = new MailetContainerModule();

        assertThat(testee.getMailetContextConfiguration(configurationProvider).size())
            .isEqualTo(0);
    }

    @Test
    void getMailetContextConfigurationShouldThrowOnInvalidXML() throws Exception {
        ConfigurationProvider configurationProvider = mock(ConfigurationProvider.class);
        when(configurationProvider.getConfiguration("mailetcontainer"))
            .thenThrow(new ConfigurationRuntimeException());

        MailetContainerModule testee = new MailetContainerModule();

        assertThatThrownBy(() -> testee.getMailetContextConfiguration(configurationProvider))
            .isInstanceOf(ConfigurationRuntimeException.class);
    }

    @Test
    void getMailetContextConfigurationShouldReturnConfigurationWhenSome() throws Exception {
        XMLConfiguration configuration = FileConfigurationProvider.getConfig(new ByteArrayInputStream((
                    "<mailetcontainer>" +
                    "  <context>" +
                    "    <key>value</key>" +
                    "  </context>" +
                    "</mailetcontainer>")
            .getBytes(StandardCharsets.UTF_8)));
        ConfigurationProvider configurationProvider = mock(ConfigurationProvider.class);
        when(configurationProvider.getConfiguration("mailetcontainer")).thenReturn(configuration);

        MailetContainerModule testee = new MailetContainerModule();

        HierarchicalConfiguration<ImmutableNode> mailetContextConfiguration = testee.getMailetContextConfiguration(configurationProvider);
        assertThat(mailetContextConfiguration.getString("key"))
            .isEqualTo("value");
    }

    @Test
    void getProcessortConfigurationShouldReturnEmptyWhenNoContextSection() throws Exception {
        ConfigurationProvider configurationProvider = mock(ConfigurationProvider.class);
        when(configurationProvider.getConfiguration("mailetcontainer"))
            .thenReturn(new BaseHierarchicalConfiguration());
        XMLConfiguration defaultConfiguration = FileConfigurationProvider.getConfig(new ByteArrayInputStream((
                "<processors>" +
                "  <key>value</key>" +
                "</processors>")
            .getBytes(StandardCharsets.UTF_8)));

        MailetModuleInitializationOperation testee = new MailetModuleInitializationOperation(configurationProvider,
            mock(CompositeProcessorImpl.class),
            NO_TRANSPORT_CHECKS,
            () -> defaultConfiguration,
            mock(JamesMailSpooler.class),
            mock(JamesMailSpooler.Configuration.class));

        assertThat(testee.getProcessorConfiguration())
            .isEqualTo(defaultConfiguration);
    }

    @Test
    void getProcessorConfigurationShouldThrowOnInvalidXML() throws Exception {
        ConfigurationProvider configurationProvider = mock(ConfigurationProvider.class);
        when(configurationProvider.getConfiguration("mailetcontainer"))
            .thenThrow(new ConfigurationRuntimeException());

        MailetModuleInitializationOperation testee = new MailetModuleInitializationOperation(configurationProvider,
            mock(CompositeProcessorImpl.class),
            NO_TRANSPORT_CHECKS,
            mock(MailetContainerModule.DefaultProcessorsConfigurationSupplier.class),
            mock(JamesMailSpooler.class),
            mock(JamesMailSpooler.Configuration.class));

        assertThatThrownBy(testee::getProcessorConfiguration)
            .isInstanceOf(ConfigurationRuntimeException.class);
    }

    @Test
    void getProcessorConfigurationShouldReturnConfigurationWhenSome() throws Exception {
        XMLConfiguration configuration = FileConfigurationProvider.getConfig(new ByteArrayInputStream((
                    "<mailetcontainer>" +
                    "  <processors>" +
                    "    <key>value</key>" +
                    "  </processors>" +
                    "</mailetcontainer>")
            .getBytes(StandardCharsets.UTF_8)));
        ConfigurationProvider configurationProvider = mock(ConfigurationProvider.class);
        when(configurationProvider.getConfiguration("mailetcontainer")).thenReturn(configuration);

        MailetModuleInitializationOperation testee = new MailetModuleInitializationOperation(configurationProvider,
            mock(CompositeProcessorImpl.class),
            NO_TRANSPORT_CHECKS,
            mock(MailetContainerModule.DefaultProcessorsConfigurationSupplier.class),
            mock(JamesMailSpooler.class),
            mock(JamesMailSpooler.Configuration.class));

        HierarchicalConfiguration<ImmutableNode> mailetContextConfiguration = testee.getProcessorConfiguration();
        assertThat(mailetContextConfiguration.getString("key"))
            .isEqualTo("value");
    }
}