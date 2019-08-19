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

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.junit.jupiter.api.Test;

class ListenerConfigurationTest {

    @Test
    void fromShouldThrowWhenClassIsNotInTheConfiguration() {
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();

        assertThatThrownBy(() -> ListenerConfiguration.from(configuration))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void fromShouldThrowWhenClassIsEmpty() {
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("class", "");

        assertThatThrownBy(() -> ListenerConfiguration.from(configuration))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void getClazzShouldReturnTheClassNameFromTheConfiguration() {
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        String expectedClazz = "MyClassName";
        configuration.addProperty("class", expectedClazz);

        ListenerConfiguration listenerConfiguration = ListenerConfiguration.from(configuration);

        assertThat(listenerConfiguration.getClazz()).isEqualTo(expectedClazz);
    }

    @Test
    void isAsyncShouldReturnConfiguredValue() {
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("class", "MyClassName");
        configuration.addProperty("async", "false");

        ListenerConfiguration listenerConfiguration = ListenerConfiguration.from(configuration);

        assertThat(listenerConfiguration.isAsync()).contains(false);
    }

    @Test
    void getGroupShouldBeEmptyByDefault() {
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("class", "MyClassName");

        ListenerConfiguration listenerConfiguration = ListenerConfiguration.from(configuration);

        assertThat(listenerConfiguration.getGroup()).isEmpty();
    }

    @Test
    void getGroupShouldContainsConfiguredValue() {
        String groupName = "Avengers";
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("class", "MyClassName");
        configuration.addProperty("group", groupName);

        ListenerConfiguration listenerConfiguration = ListenerConfiguration.from(configuration);

        assertThat(listenerConfiguration.getGroup()).contains(groupName);
    }
}
