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

public class TlsConfigurationTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void buildShouldThrowWhenNotEnabled() {
        expectedException.expect(IllegalStateException.class);

        TlsConfiguration.builder().build();
    }

    @Test
    public void buildShouldThrowWhenEnableWithoutKeystore() {
        expectedException.expect(IllegalStateException.class);

        TlsConfiguration.builder().enabled().build();
    }

    @Test
    public void selfSignedShouldThrowOnNullKeyStorePath() {
        expectedException.expect(NullPointerException.class);

        TlsConfiguration.builder()
            .enabled()
            .selfSigned(null, "abc");
    }

    @Test
    public void selfSignedShouldThrowOnNullKeyStorePassword() {
        expectedException.expect(NullPointerException.class);

        TlsConfiguration.builder()
            .enabled()
            .selfSigned("abc", null);
    }

    @Test
    public void buildShouldWorkOnDisabledHttps() {
        assertThat(
            TlsConfiguration.builder()
                .disabled()
                .build())
            .isEqualTo(new TlsConfiguration(false, null, null, null, null));
    }

    @Test
    public void buildShouldWorkOnSelfSignedHttps() {
        assertThat(
            TlsConfiguration.builder()
                .enabled()
                .selfSigned("abcd", "efgh")
                .build())
            .isEqualTo(new TlsConfiguration(true, "abcd", "efgh", null, null));
    }

    @Test
    public void buildShouldWorkOnTrustedHttps() {
        assertThat(
            TlsConfiguration.builder()
                .enabled()
                .raw("a", "b", "c", "d")
                .build())
            .isEqualTo(new TlsConfiguration(true, "a", "b", "c", "d"));
    }

    @Test
    public void shouldRespectBeanContract() {
        EqualsVerifier.forClass(TlsConfiguration.class).verify();
    }

}
