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

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class PreDeletionHookConfigurationTest {
    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(PreDeletionHookConfiguration.class)
            .verify();
    }

    @Test
    void fromShouldThrowWhenClassNameIsMissing() {
        HierarchicalConfiguration configuration = new HierarchicalConfiguration();

        assertThatThrownBy(() -> PreDeletionHookConfiguration.from(configuration))
            .isInstanceOf(ConfigurationException.class);
    }

    @Test
    void fromShouldThrowWhenClassNameIsEmpty() {
        HierarchicalConfiguration configuration = new HierarchicalConfiguration();
        configuration.addProperty("class", "");

        assertThatThrownBy(() -> PreDeletionHookConfiguration.from(configuration))
            .isInstanceOf(ConfigurationException.class);
    }

    @Test
    void fromShouldReturnValueWithCorrectClassName() throws ConfigurationException {
        HierarchicalConfiguration configuration = new HierarchicalConfiguration();
        String className = "a.class";
        configuration.addProperty("class", className);

        assertThat(PreDeletionHookConfiguration.from(configuration))
            .isEqualTo(PreDeletionHookConfiguration.forClass(className));
    }
}