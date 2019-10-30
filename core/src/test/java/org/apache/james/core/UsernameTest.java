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

import org.junit.Test;

public class UsernameTest {

    @Test
    public void fromShouldThrowOnEmptyLocalPart() {
        assertThatThrownBy(() -> Username.from("", Optional.empty()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void fromShouldThrowOnNullDomainPart() {
        assertThatThrownBy(() -> Username.from(null, Optional.empty()))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void fromShouldThrowOnLocalPartWithDomainDelimiter() {
        assertThatThrownBy(() -> Username.from("aa@bb", Optional.empty()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void fromShouldThrowOnEmptyDomain() {
        assertThatThrownBy(() -> Username.from("aa", Optional.of("")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void fromShouldThrowWhenDomainContainsDomainDelimiter() {
        assertThatThrownBy(() -> Username.from("aa", Optional.of("bb@cc")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void fromLocalPartWithDomainStringVersionShouldThrowOnNullLocalPart() {
        assertThatThrownBy(() -> Username.fromLocalPartWithDomain(null, "domain"))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void fromLocalPartWithDomainStringVersionShouldThrowOnEmptyLocalPart() {
        assertThatThrownBy(() -> Username.fromLocalPartWithDomain("", "domain"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void fromLocalPartWithDomainStringVersionShouldThrowOnLocalPartThatContainsDomainDelimiter() {
        assertThatThrownBy(() -> Username.fromLocalPartWithDomain("aa@bb", "domain"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void fromLocalPartWithDomainStringVersionShouldThrowOnNullDomainPart() {
        String domain = null;
        assertThatThrownBy(() -> Username.fromLocalPartWithDomain("local", domain))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void fromLocalPartWithDomainStringVersionShouldThrowOnEmptyDomainPart() {
        assertThatThrownBy(() -> Username.fromLocalPartWithDomain("local", ""))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void fromLocalPartWithDomainStringVersionShouldThrowOnDomainPartThatContainsDomainDelimiter() {
        assertThatThrownBy(() -> Username.fromLocalPartWithDomain("local", "aa@bb"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void fromLocalPartWithoutDomainShouldThrowOnEmpty() {
        assertThatThrownBy(() -> Username.fromLocalPartWithoutDomain(""))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void fromLocalPartWithoutDomainShouldThrowOnNull() {
        assertThatThrownBy(() -> Username.fromLocalPartWithoutDomain(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void fromLocalPartWithoutDomainShouldThrowOnUsernameThatContainsDomainDelimiter() {
        assertThatThrownBy(() -> Username.fromLocalPartWithoutDomain("aa@bb"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void fromUsernameShouldThrowOnNull() {
        assertThatThrownBy(() -> Username.of(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void fromUsernameShouldThrowOnEmpty() {
        assertThatThrownBy(() -> Username.of(""))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void fromUsernameShouldAllow255LongUsername() {
        String tail = "@a";
        assertThat(Username.of(StringUtils.repeat('j', 255 - tail.length()) + tail).asString())
            .hasSize(255);
    }

    @Test
    public void fromUsernameShouldThrowWhenTooLong() {
        String tail = "@a";
        assertThatThrownBy(() -> Username.of(StringUtils.repeat('j', 255 - tail.length() + 1) + tail))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void fromUsernameShouldThrowWhenMultipleDomainDelimiter() {
        assertThatThrownBy(() -> Username.of("aa@aa@aa"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void fromUsernameShouldThrowWhenEndsWithDomainDelimiter() {
        assertThatThrownBy(() -> Username.of("aa@"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void fromUsernameShouldThrowWhenStartsWithDomainDelimiter() {
        assertThatThrownBy(() -> Username.of("@aa"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void fromUsernameShouldParseUsernameWithDomain() {
        assertThat(Username.of("aa@bb"))
            .isEqualTo(Username.from("aa", Optional.of("bb")));
    }

    @Test
    public void fromUsernameShouldParseUsernameWithoutDomain() {
        assertThat(Username.of("aa"))
            .isEqualTo(Username.from("aa", Optional.empty()));
    }

    @Test
    public void fromLocalPartWithDomainShouldReturnAValidUser() {
        assertThat(Username.fromLocalPartWithDomain("aa", "bb"))
            .isEqualTo(Username.from("aa", Optional.of("bb")));
    }

    @Test
    public void fromLocalPartWithoutDomainShouldReturnAValidUser() {
        assertThat(Username.fromLocalPartWithoutDomain("aa"))
            .isEqualTo(Username.from("aa", Optional.empty()));
    }

    @Test
    public void hasDomainPartShouldReturnFalseWhenNoDomain() {
        assertThat(Username.fromLocalPartWithoutDomain("aa").hasDomainPart())
            .isFalse();
    }

    @Test
    public void hasDomainPartShouldReturnTrueWhenHasADomain() {
        assertThat(Username.fromLocalPartWithDomain("aa", "domain").hasDomainPart())
            .isTrue();
    }

    @Test
    public void withDefaultDomainShouldAppendDefaultDomainWhenNone() {
        assertThat(Username.of("user")
            .withDefaultDomain(Domain.LOCALHOST))
            .isEqualTo(Username.fromLocalPartWithDomain("user", Domain.LOCALHOST));
    }

    @Test
    public void withDefaultDomainShouldNotAppendDefaultDomainWhenDomainIsPresent() {
        assertThat(Username.of("user@domain")
            .withDefaultDomain(Domain.LOCALHOST))
            .isEqualTo(Username.of("user@domain"));
    }

    @Test
    public void withDefaultDomainShouldNotThrowUponEmptyDomain() {
        assertThat(Username.of("user")
            .withDefaultDomain(Optional.empty()))
            .isEqualTo(Username.of("user"));
    }

    @Test
    public void withDefaultDomainShouldNotThrowUponEmptyDomainWhenUsersHadADomain() {
        assertThat(Username.of("user@domain")
            .withDefaultDomain(Optional.empty()))
            .isEqualTo(Username.of("user@domain"));
    }

    @Test
    public void withDefaultDomainFromUserShouldPreserveUserWhenAlreadyHasADomain() {
        assertThat(Username.of("user@domain")
            .withDefaultDomainFromUser(Username.of("bob@tld")))
            .isEqualTo(Username.of("user@domain"));
    }

    @Test
    public void withDefaultDomainFromUserShouldAppendOtherUserDomainWhenNone() {
        assertThat(Username.of("user")
            .withDefaultDomainFromUser(Username.of("bob@tld")))
            .isEqualTo(Username.of("user@tld"));
    }

    @Test
    public void withDefaultDomainFromUserShouldNotThrowUponNoDomain() {
        assertThat(Username.of("user")
            .withDefaultDomainFromUser(Username.of("bob")))
            .isEqualTo(Username.of("user"));
    }
}
