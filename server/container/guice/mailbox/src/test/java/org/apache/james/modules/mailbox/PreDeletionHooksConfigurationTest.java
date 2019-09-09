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

package org.apache.james.modules.mailbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.server.core.configuration.FileConfigurationProvider;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

import nl.jqno.equalsverifier.EqualsVerifier;

class PreDeletionHooksConfigurationTest {
    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(PreDeletionHooksConfiguration.class)
            .verify();
    }

    @Test
    void fromShouldReturnNoneWhenEmpty() throws Exception {
        HierarchicalConfiguration<ImmutableNode> configuration = new BaseHierarchicalConfiguration();

        assertThat(PreDeletionHooksConfiguration.from(configuration))
            .isEqualTo(PreDeletionHooksConfiguration.none());
    }

    @Test
    void fromShouldThrowWhenInvalidHookConfiguration() throws Exception {
        XMLConfiguration configuration = FileConfigurationProvider.getConfig(new ByteArrayInputStream((
            "<preDeletionHooks>" +
                "  <preDeletionHook>" +
                "    <class></class>" +
                "  </preDeletionHook>" +
                "</preDeletionHooks>")
            .getBytes(StandardCharsets.UTF_8)));

        HierarchicalConfiguration<ImmutableNode> invalidConfigurationEntry = new BaseHierarchicalConfiguration();
        configuration.addProperty(PreDeletionHooksConfiguration.CONFIGURATION_ENTRY_NAME, ImmutableList.of(invalidConfigurationEntry));

        assertThatThrownBy(() -> PreDeletionHooksConfiguration.from(configuration))
            .isInstanceOf(ConfigurationException.class);
    }

    @Test
    void fromShouldReturnConfiguredEntry() throws Exception {
        XMLConfiguration configuration = FileConfigurationProvider.getConfig(new ByteArrayInputStream((
            "<preDeletionHooks>" +
            "  <preDeletionHook>" +
            "    <class>a.class</class>" +
            "  </preDeletionHook>" +
            "</preDeletionHooks>")
            .getBytes(StandardCharsets.UTF_8)));

        String className = "a.class";
        assertThat(PreDeletionHooksConfiguration.from(configuration))
            .isEqualTo(new PreDeletionHooksConfiguration(ImmutableList.of(PreDeletionHookConfiguration.forClass(className))));
    }

    @Test
    void fromShouldReturnAllConfiguredEntries() throws Exception {
        XMLConfiguration configuration = FileConfigurationProvider.getConfig(new ByteArrayInputStream((
            "<preDeletionHooks>" +
            "  <preDeletionHook>" +
            "    <class>a.class</class>" +
            "  </preDeletionHook>" +
            "  <preDeletionHook>" +
            "    <class>b.class</class>" +
            "  </preDeletionHook>" +
            "</preDeletionHooks>")
            .getBytes(StandardCharsets.UTF_8)));

        String className1 = "a.class";
        String className2 = "b.class";
        assertThat(PreDeletionHooksConfiguration.from(configuration))
            .isEqualTo(new PreDeletionHooksConfiguration(ImmutableList.of(
                PreDeletionHookConfiguration.forClass(className1),
                PreDeletionHookConfiguration.forClass(className2))));
    }
}