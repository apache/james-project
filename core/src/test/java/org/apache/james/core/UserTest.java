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

import org.junit.Test;

public class UserTest {

    @Test
    public void fromShouldThrowOnEmptyLocalPart() {
        assertThatThrownBy(() -> User.from("", Optional.empty()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void fromShouldThrowOnNullDomainPart() {
        assertThatThrownBy(() -> User.from(null, Optional.empty()))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void fromShouldThrowOnLocalPartWithDomainDelimiter() {
        assertThatThrownBy(() -> User.from("aa@bb", Optional.empty()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void fromShouldThrowOnEmptyDomain() {
        assertThatThrownBy(() -> User.from("aa", Optional.of("")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void fromShouldThrowWhenDomainContainsDomainDelimiter() {
        assertThatThrownBy(() -> User.from("aa", Optional.of("bb@cc")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void fromLocalPartWithDomainStringVersionShouldThrowOnNullLocalPart() {
        assertThatThrownBy(() -> User.fromLocalPartWithDomain(null, "domain"))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void fromLocalPartWithDomainStringVersionShouldThrowOnEmptyLocalPart() {
        assertThatThrownBy(() -> User.fromLocalPartWithDomain("", "domain"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void fromLocalPartWithDomainStringVersionShouldThrowOnLocalPartThatContainsDomainDelimiter() {
        assertThatThrownBy(() -> User.fromLocalPartWithDomain("aa@bb", "domain"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void fromLocalPartWithDomainStringVersionShouldThrowOnNullDomainPart() {
        String domain = null;
        assertThatThrownBy(() -> User.fromLocalPartWithDomain("local", domain))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void fromLocalPartWithDomainStringVersionShouldThrowOnEmptyDomainPart() {
        assertThatThrownBy(() -> User.fromLocalPartWithDomain("local", ""))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void fromLocalPartWithDomainStringVersionShouldThrowOnDomainPartThatContainsDomainDelimiter() {
        assertThatThrownBy(() -> User.fromLocalPartWithDomain("local", "aa@bb"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void fromLocalPartWithoutDomainShouldThrowOnEmpty() {
        assertThatThrownBy(() -> User.fromLocalPartWithoutDomain(""))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void fromLocalPartWithoutDomainShouldThrowOnNull() {
        assertThatThrownBy(() -> User.fromLocalPartWithoutDomain(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void fromLocalPartWithoutDomainShouldThrowOnUsernameThatContainsDomainDelimiter() {
        assertThatThrownBy(() -> User.fromLocalPartWithoutDomain("aa@bb"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void fromUsernameShouldThrowOnNull() {
        assertThatThrownBy(() -> User.fromUsername(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void fromUsernameShouldThrowOnEmpty() {
        assertThatThrownBy(() -> User.fromUsername(""))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void fromUsernameShouldThrowWhenMultipleDomainDelimiter() {
        assertThatThrownBy(() -> User.fromUsername("aa@aa@aa"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void fromUsernameShouldThrowWhenEndsWithDomainDelimiter() {
        assertThatThrownBy(() -> User.fromUsername("aa@"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void fromUsernameShouldThrowWhenStartsWithDomainDelimiter() {
        assertThatThrownBy(() -> User.fromUsername("@aa"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void fromUsernameShouldParseUsernameWithDomain() {
        assertThat(User.fromUsername("aa@bb"))
            .isEqualTo(User.from("aa", Optional.of("bb")));
    }

    @Test
    public void fromUsernameShouldParseUsernameWithoutDomain() {
        assertThat(User.fromUsername("aa"))
            .isEqualTo(User.from("aa", Optional.empty()));
    }

    @Test
    public void fromLocalPartWithDomainShouldReturnAValidUser() {
        assertThat(User.fromLocalPartWithDomain("aa", "bb"))
            .isEqualTo(User.from("aa", Optional.of("bb")));
    }

    @Test
    public void fromLocalPartWithoutDomainShouldReturnAValidUser() {
        assertThat(User.fromLocalPartWithoutDomain("aa"))
            .isEqualTo(User.from("aa", Optional.empty()));
    }

    @Test
    public void hasDomainPartShouldReturnFalseWhenNoDomain() {
        assertThat(User.fromLocalPartWithoutDomain("aa").hasDomainPart())
            .isFalse();
    }

    @Test
    public void hasDomainPartShouldReturnTrueWhenHasADomain() {
        assertThat(User.fromLocalPartWithDomain("aa", "domain").hasDomainPart())
            .isTrue();
    }

    @Test
    public void withDefaultDomainShouldAppendDefaultDomainWhenNone() {
        assertThat(User.fromUsername("user")
            .withDefaultDomain(Domain.LOCALHOST))
            .isEqualTo(User.fromLocalPartWithDomain("user", Domain.LOCALHOST));
    }

    @Test
    public void withDefaultDomainShouldNotAppendDefaultDomainWhenDomainIsPresent() {
        assertThat(User.fromUsername("user@domain")
            .withDefaultDomain(Domain.LOCALHOST))
            .isEqualTo(User.fromUsername("user@domain"));
    }
}
