/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ***************************************************************/

package org.apache.james.rrt.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class RecipientRewriteTableConfigurationTest {
    @Test
    void shouldRespectBeanContract() {
        EqualsVerifier.forClass(RecipientRewriteTableConfiguration.class).verify();
    }

    @Test
    void emptyConfigurationShouldHaveDefaults() throws ConfigurationException {
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        RecipientRewriteTableConfiguration recipientRewriteTableConfiguration = RecipientRewriteTableConfiguration.fromConfiguration(configuration);

        assertThat(recipientRewriteTableConfiguration.getMappingLimit())
            .isEqualTo(10);
        assertThat(recipientRewriteTableConfiguration.isRecursive())
            .isTrue();
    }

    @Test
    void negativeLimitShouldThrows() {
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("recursiveMapping", "true");
        configuration.addProperty("mappingLimit", -1);

        assertThatCode(() -> RecipientRewriteTableConfiguration.fromConfiguration(configuration))
            .isInstanceOf(ConfigurationException.class);
    }

    @Test
    void goodConfigurationShouldPopulateTheConfiguration() throws ConfigurationException {
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("recursiveMapping", "true");
        configuration.addProperty("mappingLimit", 42);
        RecipientRewriteTableConfiguration recipientRewriteTableConfiguration = RecipientRewriteTableConfiguration.fromConfiguration(configuration);

        assertThat(recipientRewriteTableConfiguration.getMappingLimit())
            .isEqualTo(42);
        assertThat(recipientRewriteTableConfiguration.isRecursive())
            .isTrue();
    }

    @Test
    void goodConfigurationWithoutMappingShouldHaveANullMappingLevel() throws ConfigurationException {
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("recursiveMapping", "false");
        configuration.addProperty("mappingLimit", 42);
        RecipientRewriteTableConfiguration recipientRewriteTableConfiguration = RecipientRewriteTableConfiguration.fromConfiguration(configuration);

        assertThat(recipientRewriteTableConfiguration.getMappingLimit())
            .isEqualTo(0);
        assertThat(recipientRewriteTableConfiguration.isRecursive())
            .isFalse();
    }
}