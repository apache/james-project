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

import org.apache.commons.configuration.DefaultConfigurationBuilder;
import org.junit.Test;

public class ListenerConfigurationTest {

    @Test
    public void fromShouldThrowWhenClassIsNotInTheConfiguration() {
        DefaultConfigurationBuilder configuration = new DefaultConfigurationBuilder();

        assertThatThrownBy(() -> ListenerConfiguration.from(configuration))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void fromShouldThrowWhenClassIsEmpty() {
        DefaultConfigurationBuilder configuration = new DefaultConfigurationBuilder();
        configuration.addProperty("class", "");

        assertThatThrownBy(() -> ListenerConfiguration.from(configuration))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void getClazzShouldReturnTheClassNameFromTheConfiguration() {
        DefaultConfigurationBuilder configuration = new DefaultConfigurationBuilder();
        String expectedClazz = "MyClassName";
        configuration.addProperty("class", expectedClazz);

        ListenerConfiguration listenerConfiguration = ListenerConfiguration.from(configuration);

        assertThat(listenerConfiguration.getClazz()).isEqualTo(expectedClazz);
    }

    @Test
    public void isAsyncShouldReturnConfiguredValue() {
        DefaultConfigurationBuilder configuration = new DefaultConfigurationBuilder();
        String expectedClazz = "MyClassName";
        configuration.addProperty("class", expectedClazz);
        configuration.addProperty("async", "false");

        ListenerConfiguration listenerConfiguration = ListenerConfiguration.from(configuration);

        assertThat(listenerConfiguration.isAsync()).contains(false);
    }
}
