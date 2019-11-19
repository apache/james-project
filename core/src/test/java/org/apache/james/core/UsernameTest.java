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

package org.apache.james.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

class UsernameTest {

    @Test
    void fromShouldThrowOnEmptyLocalPart() {
        assertThatThrownBy(() -> Username.from("", Optional.empty()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromShouldThrowOnNullDomainPart() {
        assertThatThrownBy(() -> Username.from(null, Optional.empty()))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void fromShouldThrowOnLocalPartWithDomainDelimiter() {
        assertThatThrownBy(() -> Username.from("aa@bb", Optional.empty()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromShouldThrowOnEmptyDomain() {
        assertThatThrownBy(() -> Username.from("aa", Optional.of("")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromShouldThrowWhenDomainContainsDomainDelimiter() {
        assertThatThrownBy(() -> Username.from("aa", Optional.of("bb@cc")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromLocalPartWithDomainStringVersionShouldThrowOnNullLocalPart() {
        assertThatThrownBy(() -> Username.fromLocalPartWithDomain(null, "domain"))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void fromLocalPartWithDomainStringVersionShouldThrowOnEmptyLocalPart() {
        assertThatThrownBy(() -> Username.fromLocalPartWithDomain("", "domain"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromLocalPartWithDomainStringVersionShouldThrowOnLocalPartThatContainsDomainDelimiter() {
        assertThatThrownBy(() -> Username.fromLocalPartWithDomain("aa@bb", "domain"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromLocalPartWithDomainStringVersionShouldThrowOnNullDomainPart() {
        String domain = null;
        assertThatThrownBy(() -> Username.fromLocalPartWithDomain("local", domain))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void fromLocalPartWithDomainStringVersionShouldThrowOnEmptyDomainPart() {
        assertThatThrownBy(() -> Username.fromLocalPartWithDomain("local", ""))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromLocalPartWithDomainStringVersionShouldThrowOnDomainPartThatContainsDomainDelimiter() {
        assertThatThrownBy(() -> Username.fromLocalPartWithDomain("local", "aa@bb"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromLocalPartWithoutDomainShouldThrowOnEmpty() {
        assertThatThrownBy(() -> Username.fromLocalPartWithoutDomain(""))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromLocalPartWithoutDomainShouldThrowOnNull() {
        assertThatThrownBy(() -> Username.fromLocalPartWithoutDomain(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void fromLocalPartWithoutDomainShouldThrowOnUsernameThatContainsDomainDelimiter() {
        assertThatThrownBy(() -> Username.fromLocalPartWithoutDomain("aa@bb"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromUsernameShouldThrowOnNull() {
        assertThatThrownBy(() -> Username.of(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromUsernameShouldThrowOnEmpty() {
        assertThatThrownBy(() -> Username.of(""))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromUsernameShouldAllow255LongUsername() {
        String tail = "@a";
        assertThat(Username.of(StringUtils.repeat('j', 255 - tail.length()) + tail).asString())
            .hasSize(255);
    }

    @Test
    void fromUsernameShouldThrowWhenTooLong() {
        String tail = "@a";
        assertThatThrownBy(() -> Username.of(StringUtils.repeat('j', 255 - tail.length() + 1) + tail))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromUsernameShouldThrowWhenMultipleDomainDelimiter() {
        assertThatThrownBy(() -> Username.of("aa@aa@aa"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromUsernameShouldThrowWhenEndsWithDomainDelimiter() {
        assertThatThrownBy(() -> Username.of("aa@"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromUsernameShouldThrowWhenStartsWithDomainDelimiter() {
        assertThatThrownBy(() -> Username.of("@aa"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromUsernameShouldParseUsernameWithDomain() {
        assertThat(Username.of("aa@bb"))
            .isEqualTo(Username.from("aa", Optional.of("bb")));
    }

    @Test
    void fromUsernameShouldParseUsernameWithoutDomain() {
        assertThat(Username.of("aa"))
            .isEqualTo(Username.from("aa", Optional.empty()));
    }

    @Test
    void fromLocalPartWithDomainShouldReturnAValidUser() {
        assertThat(Username.fromLocalPartWithDomain("aa", "bb"))
            .isEqualTo(Username.from("aa", Optional.of("bb")));
    }

    @Test
    void fromLocalPartWithoutDomainShouldReturnAValidUser() {
        assertThat(Username.fromLocalPartWithoutDomain("aa"))
            .isEqualTo(Username.from("aa", Optional.empty()));
    }

    @Test
    void hasDomainPartShouldReturnFalseWhenNoDomain() {
        assertThat(Username.fromLocalPartWithoutDomain("aa").hasDomainPart())
            .isFalse();
    }

    @Test
    void hasDomainPartShouldReturnTrueWhenHasADomain() {
        assertThat(Username.fromLocalPartWithDomain("aa", "domain").hasDomainPart())
            .isTrue();
    }

    @Test
    void withDefaultDomainShouldAppendDefaultDomainWhenNone() {
        assertThat(Username.of("user")
            .withDefaultDomain(Domain.LOCALHOST))
            .isEqualTo(Username.fromLocalPartWithDomain("user", Domain.LOCALHOST));
    }

    @Test
    void withDefaultDomainShouldNotAppendDefaultDomainWhenDomainIsPresent() {
        assertThat(Username.of("user@domain")
            .withDefaultDomain(Domain.LOCALHOST))
            .isEqualTo(Username.of("user@domain"));
    }

    @Test
    void withDefaultDomainShouldNotThrowUponEmptyDomain() {
        assertThat(Username.of("user")
            .withDefaultDomain(Optional.empty()))
            .isEqualTo(Username.of("user"));
    }

    @Test
    void withDefaultDomainShouldNotThrowUponEmptyDomainWhenUsersHadADomain() {
        assertThat(Username.of("user@domain")
            .withDefaultDomain(Optional.empty()))
            .isEqualTo(Username.of("user@domain"));
    }

    @Test
    void withDefaultDomainFromUserShouldPreserveUserWhenAlreadyHasADomain() {
        assertThat(Username.of("user@domain")
            .withDefaultDomainFromUser(Username.of("bob@tld")))
            .isEqualTo(Username.of("user@domain"));
    }

    @Test
    void withDefaultDomainFromUserShouldAppendOtherUserDomainWhenNone() {
        assertThat(Username.of("user")
            .withDefaultDomainFromUser(Username.of("bob@tld")))
            .isEqualTo(Username.of("user@tld"));
    }

    @Test
    void withDefaultDomainFromUserShouldNotThrowUponNoDomain() {
        assertThat(Username.of("user")
            .withDefaultDomainFromUser(Username.of("bob")))
            .isEqualTo(Username.of("user"));
    }

    @Test
    void equalsAsIdShouldReturnFalseWhenNull() {
        assertThat(Username.of("user").equalsAsId((String)null))
            .isFalse();
    }

    @Test
    void equalsAsIdShouldReturnFalseWhenDifferentId() {
        assertThat(Username.of("user").equalsAsId("user2"))
            .isFalse();
    }

    @Test
    void equalsAsIdShouldReturnTrueWhenSameId() {
        assertThat(Username.of("user").equalsAsId("user"))
            .isTrue();
    }

    @Test
    void equalsAsIdShouldReturnTrueWhenSameIdWithDifferentCase() {
        assertThat(Username.of("user").equalsAsId("uSEr"))
            .isTrue();
    }

    @Test
    void equalsAsIdForUsernameShouldThrowWhenNull() {
        assertThatThrownBy(() -> Username.of("user").equalsAsId((Username)null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void equalsAsIdForUsernameShouldReturnFalseWhenDifferentId() {
        assertThat(Username.of("user").equalsAsId(Username.of("user2")))
            .isFalse();
    }

    @Test
    void equalsAsIdForUsernameShouldReturnTrueWhenSameId() {
        assertThat(Username.of("user").equalsAsId(Username.of("user")))
            .isTrue();
    }

    @Test
    void equalsAsIdForUsernameShouldReturnTrueWhenSameIdWithDifferentCase() {
        assertThat(Username.of("user").equalsAsId(Username.of("uSEr")))
            .isTrue();
    }
}
