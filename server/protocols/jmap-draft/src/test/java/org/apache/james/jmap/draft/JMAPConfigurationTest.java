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

package org.apache.james.jmap.draft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.apache.james.jmap.draft.JMAPConfiguration;
import org.junit.Test;

public class JMAPConfigurationTest {

    public static final boolean ENABLED = true;
    public static final boolean DISABLED = false;

    @Test
    public void buildShouldThrowWhenKeystoreIsNull() {
        assertThatThrownBy(() -> JMAPConfiguration.builder()
                .enable()
                .keystore(null)
                .build())
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("'keystore' is mandatory");
    }

    @Test
    public void buildShouldThrowWhenKeystoreIsEmpty() {
        assertThatThrownBy(() -> JMAPConfiguration.builder()
                .enable()
                .keystore("")
                .build())
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("'keystore' is mandatory");
    }

    @Test
    public void buildShouldThrowWhenSecretIsNull() {
        assertThatThrownBy(() -> JMAPConfiguration.builder()
                .enable()
                .keystore("keystore")
                .secret(null)
                .build())
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("'secret' is mandatory");
    }

    @Test
    public void buildShouldThrowWhenSecretIsEmpty() {
        assertThatThrownBy(() -> JMAPConfiguration.builder()
                .enable()
                .keystore("keystore")
                .secret("")
                .build())
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("'secret' is mandatory");
    }

    @Test
    public void buildShouldThrowWhenJwtPublicKeyPemIsNull() {
        assertThatThrownBy(() -> JMAPConfiguration.builder()
                .enable()
                .keystore("keystore")
                .secret("secret")
                .jwtPublicKeyPem(null)
                .build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void buildShouldThrowWhenJwtPublicKeyPemIsEmpty() {
        assertThatThrownBy(() -> JMAPConfiguration.builder()
            .enable()
            .keystore("keystore")
            .secret("secret")
            .jwtPublicKeyPem(Optional.empty())
            .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void buildShouldWorkWhenRandomPort() {
        JMAPConfiguration expectedJMAPConfiguration = new JMAPConfiguration(ENABLED, "keystore", "secret", Optional.of("file://conf/jwt_publickey"), Optional.empty());

        JMAPConfiguration jmapConfiguration = JMAPConfiguration.builder()
            .enable()
            .keystore("keystore")
            .secret("secret")
            .jwtPublicKeyPem(Optional.of("file://conf/jwt_publickey"))
            .randomPort()
            .build();
        assertThat(jmapConfiguration).isEqualToComparingFieldByField(expectedJMAPConfiguration);
    }

    @Test
    public void buildShouldWorkWhenFixedPort() {
        JMAPConfiguration expectedJMAPConfiguration = new JMAPConfiguration(ENABLED, "keystore", "secret", Optional.of("file://conf/jwt_publickey"), Optional.of(80));

        JMAPConfiguration jmapConfiguration = JMAPConfiguration.builder()
            .enable()
            .keystore("keystore")
            .secret("secret")
            .jwtPublicKeyPem(Optional.of("file://conf/jwt_publickey"))
            .port(80)
            .build();
        assertThat(jmapConfiguration).isEqualToComparingFieldByField(expectedJMAPConfiguration);
    }

    @Test
    public void buildShouldWorkWhenDisabled() {
        String keystore = null;
        String secret = null;
        Optional<String> jwtPublicKeyPem = Optional.empty();
        Optional<Integer> port = Optional.empty();
        JMAPConfiguration expectedJMAPConfiguration = new JMAPConfiguration(DISABLED, keystore, secret, jwtPublicKeyPem, port);

        JMAPConfiguration jmapConfiguration = JMAPConfiguration.builder()
            .disable()
            .build();
        assertThat(jmapConfiguration).isEqualToComparingFieldByField(expectedJMAPConfiguration);
    }
}
