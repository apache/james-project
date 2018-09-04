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
package org.apache.james.backend.mailqueue;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.commons.configuration.PropertiesConfiguration;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class RabbitMQConfigurationTest {

    @Test
    public void shouldRespectBeanContract() {
        EqualsVerifier.forClass(RabbitMQConfiguration.class).verify();
    }

    @Test
    public void fromShouldThrowWhenURIIsNotInTheConfiguration() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();

        assertThatThrownBy(() -> RabbitMQConfiguration.from(configuration))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("You need to specify the URI of RabbitMQ");
    }

    @Test
    public void fromShouldThrowWhenURIIsNull() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("uri", null);

        assertThatThrownBy(() -> RabbitMQConfiguration.from(configuration))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("You need to specify the URI of RabbitMQ");
    }

    @Test
    public void fromShouldThrowWhenURIIsEmpty() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("uri", "");

        assertThatThrownBy(() -> RabbitMQConfiguration.from(configuration))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("You need to specify the URI of RabbitMQ");
    }

    @Test
    public void fromShouldThrowWhenURIIsInvalid() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("uri", ":invalid");

        assertThatThrownBy(() -> RabbitMQConfiguration.from(configuration))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("You need to specify a valid URI");
    }
}
