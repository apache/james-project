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

package org.apache.james.jmap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.apache.james.util.Port;
import org.junit.jupiter.api.Test;

class JMAPConfigurationTest {

    public static final boolean ENABLED = true;
    public static final boolean DISABLED = false;

    @Test
    void buildShouldThrowWhenEnableIsMissing() {
        assertThatThrownBy(() -> JMAPConfiguration.builder().build())
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("You should specify if JMAP server should be started");
    }

    @Test
    void buildShouldWorkWhenRandomPort() {
        JMAPConfiguration expectedJMAPConfiguration = new JMAPConfiguration(ENABLED, Optional.empty(), ENABLED, ENABLED, Version.RFC8621, Optional.empty());

        JMAPConfiguration jmapConfiguration = JMAPConfiguration.builder()
            .enable()
            .randomPort()
            .enableEmailQueryView()
            .build();
        assertThat(jmapConfiguration).isEqualToComparingFieldByField(expectedJMAPConfiguration);
    }

    @Test
    void buildShouldWorkWhenFixedPort() {
        JMAPConfiguration expectedJMAPConfiguration = new JMAPConfiguration(ENABLED, Optional.of(Port.of(80)), ENABLED, ENABLED, Version.RFC8621, Optional.empty());

        JMAPConfiguration jmapConfiguration = JMAPConfiguration.builder()
            .enable()
            .port(Port.of(80))
            .enableEmailQueryView()
            .enableUserProvisioning()
            .build();

        assertThat(jmapConfiguration).isEqualToComparingFieldByField(expectedJMAPConfiguration);
    }

    @Test
    void buildShouldWorkWhenDisabled() {
        JMAPConfiguration expectedJMAPConfiguration = new JMAPConfiguration(DISABLED, Optional.empty(), DISABLED, DISABLED, Version.RFC8621, Optional.empty());

        JMAPConfiguration jmapConfiguration = JMAPConfiguration.builder()
            .disable()
            .disableEmailQueryView()
            .disableUserProvisioning()
            .build();
        assertThat(jmapConfiguration).isEqualToComparingFieldByField(expectedJMAPConfiguration);
    }

    @Test
    void buildShouldWorkWithPemTls() {
        JMAPConfiguration jmapConfiguration = JMAPConfiguration.builder()
            .enable()
            .randomPort()
            .certificates("file://conf/cert.pem")
            .privateKey("file://conf/private.key")
            .secret("secret")
            .build();

        assertThat(jmapConfiguration.isTlsEnabled()).isTrue();
        assertThat(jmapConfiguration.getCertificates()).contains("file://conf/cert.pem");
        assertThat(jmapConfiguration.getPrivateKey()).contains("file://conf/private.key");
        assertThat(jmapConfiguration.getSecret()).contains("secret");
        assertThat(jmapConfiguration.getKeystore()).isEmpty();
    }

    @Test
    void buildShouldWorkWithKeystoreTls() {
        JMAPConfiguration jmapConfiguration = JMAPConfiguration.builder()
            .enable()
            .randomPort()
            .keystore("file://conf/keystore")
            .keystoreType("PKCS12")
            .secret("secret")
            .build();

        assertThat(jmapConfiguration.isTlsEnabled()).isTrue();
        assertThat(jmapConfiguration.getKeystore()).contains("file://conf/keystore");
        assertThat(jmapConfiguration.getKeystoreType()).contains("PKCS12");
        assertThat(jmapConfiguration.getSecret()).contains("secret");
        assertThat(jmapConfiguration.getCertificates()).isEmpty();
        assertThat(jmapConfiguration.getPrivateKey()).isEmpty();
    }
}
