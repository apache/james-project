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

import org.junit.Test;

public class JMAPDraftConfigurationTest {

    public static final boolean ENABLED = true;
    public static final boolean DISABLED = false;

    @Test
    public void buildShouldThrowWhenKeystoreIsNull() {
        assertThatThrownBy(() -> JMAPDraftConfiguration.builder()
                .enable()
                .keystore(null)
                .build())
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("'keystore' is mandatory");
    }

    @Test
    public void buildShouldThrowWhenKeystoreIsEmpty() {
        assertThatThrownBy(() -> JMAPDraftConfiguration.builder()
                .enable()
                .keystore("")
                .build())
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("'keystore' is mandatory");
    }

    @Test
    public void buildShouldThrowWhenSecretIsNull() {
        assertThatThrownBy(() -> JMAPDraftConfiguration.builder()
                .enable()
                .keystore("keystore")
                .secret(null)
                .build())
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("'secret' is mandatory");
    }

    @Test
    public void buildShouldThrowWhenSecretIsEmpty() {
        assertThatThrownBy(() -> JMAPDraftConfiguration.builder()
                .enable()
                .keystore("keystore")
                .secret("")
                .build())
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("'secret' is mandatory");
    }

    @Test
    public void buildShouldThrowWhenJwtPublicKeyPemIsNull() {
        assertThatThrownBy(() -> JMAPDraftConfiguration.builder()
                .enable()
                .keystore("keystore")
                .secret("secret")
                .jwtPublicKeyPem(null)
                .build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void buildShouldThrowWhenJwtPublicKeyPemIsEmpty() {
        assertThatThrownBy(() -> JMAPDraftConfiguration.builder()
            .enable()
            .keystore("keystore")
            .secret("secret")
            .jwtPublicKeyPem(Optional.empty())
            .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void buildShouldWorkWhenDisabled() {
        String keystore = null;
        String secret = null;
        Optional<String> jwtPublicKeyPem = Optional.empty();
        JMAPDraftConfiguration expectedJMAPDraftConfiguration = new JMAPDraftConfiguration(DISABLED, keystore, secret, jwtPublicKeyPem);

        JMAPDraftConfiguration jmapDraftConfiguration = JMAPDraftConfiguration.builder()
            .disable()
            .build();
        assertThat(jmapDraftConfiguration).isEqualToComparingFieldByField(expectedJMAPDraftConfiguration);
    }
}
