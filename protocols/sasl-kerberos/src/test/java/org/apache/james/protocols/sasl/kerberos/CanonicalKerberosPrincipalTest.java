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

package org.apache.james.protocols.sasl.kerberos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CanonicalKerberosPrincipalTest {
    @Test
    void shouldParseCanonicalPrincipal() {
        assertThat(CanonicalKerberosPrincipal.parse("alice@EXAMPLE.COM"))
            .isEqualTo(new CanonicalKerberosPrincipal("alice", "EXAMPLE.COM"));
    }

    @Test
    void shouldParseMultiComponentPrincipal() {
        assertThat(CanonicalKerberosPrincipal.parse("alice/admin@EXAMPLE.COM"))
            .isEqualTo(new CanonicalKerberosPrincipal("alice/admin", "EXAMPLE.COM"));
    }

    @Test
    void asStringShouldRoundTripTheParsedIdentity() {
        assertThat(CanonicalKerberosPrincipal.parse("alice@EXAMPLE.COM").asString()).isEqualTo("alice@EXAMPLE.COM");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Alice@EXAMPLE.COM",
        "alice@example.com",
        "alice@Example.Com",
        "",
        "@EXAMPLE.COM",
        "alicé@EXAMPLE.COM",
        // U+212A KELVIN SIGN lower cases to an ASCII 'k', colliding with the alice@EXAMPLE.K principal.
        "alice@EXAMPLE.K"})
    void shouldRejectNonCanonicalIdentities(String authenticationId) {
        assertThatThrownBy(() -> CanonicalKerberosPrincipal.parse(authenticationId))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNullIdentity() {
        assertThatThrownBy(() -> CanonicalKerberosPrincipal.parse(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("GSSAPI authentication identity must contain only ASCII characters");
    }

    @Test
    void constructorShouldRejectUpperCaseComponents() {
        assertThatThrownBy(() -> new CanonicalKerberosPrincipal("Alice", "EXAMPLE.COM"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Kerberos principal components must be lower case");
    }

    @Test
    void constructorShouldRejectLowerCaseRealm() {
        assertThatThrownBy(() -> new CanonicalKerberosPrincipal("alice", "example.com"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Kerberos realm must be upper case");
    }

    @Test
    void constructorShouldRejectComponentsCarryingARealm() {
        assertThatThrownBy(() -> new CanonicalKerberosPrincipal("alice@example.com", "EXAMPLE.COM"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Kerberos principal components must not be empty nor contain a realm separator");
    }

    @Test
    void constructorShouldRejectEmptyRealm() {
        assertThatThrownBy(() -> new CanonicalKerberosPrincipal("alice", ""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Kerberos realm must not be empty");
    }

    @Test
    void constructorShouldAcceptCaselessComponents() {
        assertThatCode(() -> new CanonicalKerberosPrincipal("alice.1", "EXAMPLE.COM")).doesNotThrowAnyException();
    }
}
