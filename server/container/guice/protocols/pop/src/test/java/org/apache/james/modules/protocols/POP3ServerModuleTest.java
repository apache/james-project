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

package org.apache.james.modules.protocols;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

class POP3ServerModuleTest {
    private final POP3ServerModule testee = new POP3ServerModule();

    @Test
    void retrieveSaslMechanismFactoryClassNamesShouldReturnEmptyWhenAbsent() throws Exception {
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();

        ImmutableList<String> mechanismFactoryClassNames = testee.retrieveSaslMechanismFactoryClassNames(configuration);

        assertThat(mechanismFactoryClassNames).isEmpty();
    }

    @Test
    void retrieveSaslMechanismFactoryClassNamesShouldPreserveConfiguredOrder() throws Exception {
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("auth.saslMechanisms",
            "XOauth2SaslMechanismFactory,PlainSaslMechanismFactory,com.example.CustomSaslMechanismFactory");

        ImmutableList<String> mechanismFactoryClassNames = testee.retrieveSaslMechanismFactoryClassNames(configuration);

        assertThat(mechanismFactoryClassNames)
            .containsExactly("XOauth2SaslMechanismFactory", "PlainSaslMechanismFactory", "com.example.CustomSaslMechanismFactory");
    }

    @Test
    void retrieveSaslMechanismFactoryClassNamesShouldRejectBlankConfiguredList() {
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("auth.saslMechanisms", " ");

        assertThatThrownBy(() -> testee.retrieveSaslMechanismFactoryClassNames(configuration))
            .isInstanceOf(ConfigurationException.class);
    }

    @Test
    void retrieveSaslMechanismFactoryClassNamesShouldRejectBlankEntry() {
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("auth.saslMechanisms", "PlainSaslMechanismFactory,,XOauth2SaslMechanismFactory");

        assertThatThrownBy(() -> testee.retrieveSaslMechanismFactoryClassNames(configuration))
            .isInstanceOf(ConfigurationException.class);
    }
}
