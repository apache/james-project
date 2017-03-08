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

package org.apache.james.webadmin;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import nl.jqno.equalsverifier.EqualsVerifier;

public class WebAdminConfigurationTest {

    public static final FixedPort PORT = new FixedPort(80);

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void buildShouldThrowWhenNoPortButEnabled() {
        expectedException.expect(IllegalStateException.class);

        WebAdminConfiguration.builder().enabled().build();
    }

    @Test
    public void buildShouldWorkWithoutPortWhenDisabled() {
        assertThat(WebAdminConfiguration.builder()
            .disabled()
            .build())
            .isEqualTo(new WebAdminConfiguration(false, null));
    }

    @Test
    public void buildShouldFailOnNoEnable() {
        expectedException.expect(IllegalStateException.class);

        WebAdminConfiguration.builder().port(PORT).build();
    }

    @Test
    public void builderShouldBuildRightObject() {
        assertThat(
            WebAdminConfiguration.builder()
                .enabled()
                .port(PORT)
                .build())
            .isEqualTo(new WebAdminConfiguration(true, PORT));
    }

    @Test
    public void shouldMatchBeanContract() {
        EqualsVerifier.forClass(WebAdminConfiguration.class).verify();
    }

}
