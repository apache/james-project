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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class TlsConfigurationTest {
    @Test
    void buildShouldThrowWhenNotEnabled() {
        assertThatThrownBy(() -> TlsConfiguration.builder().build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void buildShouldThrowWhenEnableWithoutKeystore() {
        assertThatThrownBy(() -> TlsConfiguration.builder().build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void selfSignedShouldThrowOnNullKeyStorePath() {
        assertThatThrownBy(() -> TlsConfiguration.builder()
            .selfSigned(null, "abc"))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void selfSignedShouldThrowOnNullKeyStorePassword() {
        assertThatThrownBy(() -> TlsConfiguration.builder()
            .selfSigned("abc", null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void buildShouldWorkOnSelfSignedHttps() {
        assertThat(
            TlsConfiguration.builder()
                .selfSigned("abcd", "efgh")
                .build())
            .isEqualTo(new TlsConfiguration("abcd", "efgh", null, null));
    }

    @Test
    void buildShouldWorkOnTrustedHttps() {
        assertThat(
            TlsConfiguration.builder()
                .raw("a", "b", "c", "d")
                .build())
            .isEqualTo(new TlsConfiguration("a", "b", "c", "d"));
    }

    @Test
    void shouldRespectBeanContract() {
        EqualsVerifier.forClass(TlsConfiguration.class).verify();
    }

}
