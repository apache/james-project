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
package org.apache.james.rrt.lib;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.rrt.lib.Mapping.Type;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class MappingTest {

    @Test
    public void hasPrefixShouldReturnTrueWhenRegex() {
        boolean hasPrefix = Mapping.Type.hasPrefix(Type.Regex.asPrefix() + "myRegex");
        assertThat(hasPrefix).isTrue();
    }

    @Test
    public void hasPrefixShouldReturnTrueWhenDomain() {
        boolean hasPrefix = Mapping.Type.hasPrefix(Type.Domain.asPrefix() + "myRegex");
        assertThat(hasPrefix).isTrue();
    }

    @Test
    public void hasPrefixShouldReturnTrueWhenError() {
        boolean hasPrefix = Mapping.Type.hasPrefix(Type.Error.asPrefix() + "myRegex");
        assertThat(hasPrefix).isTrue();
    }

    @Test
    public void hasPrefixShouldReturnTrueWhenForward() {
        boolean hasPrefix = Mapping.Type.hasPrefix(Type.Forward.asPrefix() + "myRegex");
        assertThat(hasPrefix).isTrue();
    }

    @Test
    public void hasPrefixShouldReturnFalseWhenAddress() {
        boolean hasPrefix = Mapping.Type.hasPrefix(Type.Address.asPrefix() + "myRegex");
        assertThat(hasPrefix).isFalse();
    }

    @Test
    public void detectTypeShouldReturnAddressWhenAddressPrefix() {
        assertThat(Mapping.detectType(Type.Address.asPrefix() + "mapping"))
            .isEqualTo(Type.Address);
    }

    @Test
    public void detectTypeShouldReturnRegexWhenRegexPrefix() {
        assertThat(Mapping.detectType(Type.Regex.asPrefix() + "mapping"))
            .isEqualTo(Type.Regex);
    }

    @Test
    public void detectTypeShouldReturnErrorWhenErrorPrefix() {
        assertThat(Mapping.detectType(Type.Error.asPrefix() + "mapping"))
            .isEqualTo(Type.Error);
    }

    @Test
    public void detectTypeShouldReturnDomainWhenDomainPrefix() {
        assertThat(Mapping.detectType(Type.Domain.asPrefix() + "mapping"))
            .isEqualTo(Type.Domain);
    }

    @Test
    public void detectTypeShouldReturnForwardWhenForwardPrefix() {
        assertThat(Mapping.detectType(Type.Forward.asPrefix() + "mapping"))
            .isEqualTo(Type.Forward);
    }

    @Test
    public void withoutPrefixShouldRemoveAddressPrefix() {
        assertThat(Type.Address.withoutPrefix(Type.Address.asPrefix() + "mapping"))
            .isEqualTo("mapping");
    }

    @Test
    public void withoutPrefixShouldDoNothingWhenAddressAndNoPrefix() {
        assertThat(Type.Address.withoutPrefix("mapping"))
            .isEqualTo("mapping");
    }

    @Test
    public void withoutPrefixShouldRemoveDomainPrefix() {
        assertThat(Type.Domain.withoutPrefix(Type.Domain.asPrefix() + "mapping"))
            .isEqualTo("mapping");
    }

    @Test
    public void withoutPrefixShouldRemoveErrorPrefix() {
        assertThat(Type.Error.withoutPrefix(Type.Error.asPrefix() + "mapping"))
            .isEqualTo("mapping");
    }

    @Test
    public void withoutPrefixShouldRemoveRegexPrefix() {
        assertThat(Type.Regex.withoutPrefix(Type.Regex.asPrefix() + "mapping"))
            .isEqualTo("mapping");
    }

    @Test
    public void withoutPrefixShouldRemoveForwardPrefix() {
        assertThat(Type.Forward.withoutPrefix(Type.Forward.asPrefix() + "mapping"))
            .isEqualTo("mapping");
    }

    @Test
    public void withoutPrefixShouldThrowOnBadPrefix() {
        assertThatThrownBy(() -> Type.Regex.withoutPrefix(Type.Domain.asPrefix() + "mapping"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void withoutPrefixShouldThrowWhenNoPrefix() {
        assertThatThrownBy(() -> Type.Regex.withoutPrefix("mapping"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void withoutPrefixShouldThrowWhenNoPrefixOnForwardType() {
        assertThatThrownBy(() -> Type.Forward.withoutPrefix("mapping"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void beanShouldRespectBeanContract() {
        EqualsVerifier.forClass(Mapping.Impl.class)
            .verify();
    }

    @Test
    public void addressFactoryMethodShouldThrowOnNull() {
        assertThatThrownBy(() -> Mapping.address(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void regexFactoryMethodShouldThrowOnNull() {
        assertThatThrownBy(() -> Mapping.regex(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void domainFactoryMethodShouldThrowOnNull() {
        assertThatThrownBy(() -> Mapping.domain(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void errorFactoryMethodShouldThrowOnNull() {
        assertThatThrownBy(() -> Mapping.error(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void forwardFactoryMethodShouldThrowOnNull() {
        assertThatThrownBy(() -> Mapping.forward(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void groupFactoryMethodShouldThrowOnNull() {
        assertThatThrownBy(() -> Mapping.group(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void hasDomainShouldReturnTrueWhenMappingContainAtMark() {
        assertThat(Mapping.address("a@b").hasDomain()).isTrue();
    }

    @Test
    public void hasDomainShouldReturnFalseWhenMappingIsEmpty() {
        assertThat(Mapping.address("").hasDomain()).isFalse();
    }

    @Test
    public void hasDomainShouldReturnFalseWhenMappingIsBlank() {
        assertThat(Mapping.address(" ").hasDomain()).isFalse();
    }

    @Test
    public void hasDomainShouldReturnFalseWhenMappingDoesntContainAtMark() {
        assertThat(Mapping.address("abc").hasDomain()).isFalse();
    }

    @Test
    public void appendDefaultDomainShouldWorkOnValidDomain() {
        assertThat(Mapping.address("abc").appendDomainIfNone(() -> Domain.of("domain"))).isEqualTo(Mapping.address("abc@domain"));
    }

    @Test
    public void appendDefaultDomainShouldNotAddDomainWhenMappingAlreadyContainsDomains() {
        assertThat(Mapping.address("abc@d").appendDomainIfNone(() -> Domain.of("domain"))).isEqualTo(Mapping.address("abc@d"));
    }

    @Test
    public void appendDomainShouldThrowWhenNullDomain() {
        assertThatThrownBy(() -> Mapping.address("abc@d").appendDomainIfNone(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    public void getTypeShouldReturnAddressWhenNoPrefix() {
        assertThat(Mapping.address("abc").getType()).isEqualTo(Mapping.Type.Address);
    }

    @Test
    public void getTypeShouldReturnAddressWhenEmpty() {
        assertThat(Mapping.address("").getType()).isEqualTo(Mapping.Type.Address);
    }

    @Test
    public void getTypeShouldReturnRegexWhenRegexPrefix() {
        assertThat(Mapping.regex("abc").getType()).isEqualTo(Mapping.Type.Regex);
    }

    @Test
    public void getTypeShouldReturnErrorWhenErrorPrefix() {
        assertThat(Mapping.error("abc").getType()).isEqualTo(Mapping.Type.Error);
    }

    @Test
    public void getTypeShouldReturnDomainWhenDomainPrefix() {
        assertThat(Mapping.domain(Domain.of("abc")).getType()).isEqualTo(Mapping.Type.Domain);
    }

    @Test
    public void getTypeShouldReturnForwardWhenForwardPrefix() {
        assertThat(Mapping.forward("abc").getType()).isEqualTo(Mapping.Type.Forward);
    }

    @Test
    public void getTypeShouldReturnGroupWhenGroupPrefix() {
        assertThat(Mapping.group("abc").getType()).isEqualTo(Mapping.Type.Group);
    }

    @Test
    public void getErrorMessageShouldThrowWhenMappingIsNotAnError() {
        assertThatThrownBy(() -> Mapping.domain(Domain.of("toto")).getErrorMessage())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void getErrorMessageShouldReturnMessageWhenErrorWithMessage() {
        assertThat(Mapping.error("toto").getErrorMessage()).isEqualTo("toto");
    }

    @Test
    public void getErrorMessageShouldReturnWhenErrorWithoutMessage() {
        assertThat(Mapping.error("").getErrorMessage()).isEqualTo("");
    }

    @Test
    public void asMailAddressShouldReturnMappingValueForAddress() throws Exception {
        assertThat(Mapping.address("value@domain").asMailAddress())
            .contains(new MailAddress("value@domain"));
    }

    @Test
    public void asMailAddressShouldReturnEmptyOnInvalidAddress() {
        assertThat(Mapping.address("value").asMailAddress())
            .isEmpty();
    }

    @Test
    public void asMailAddressShouldReturnEmptyForError() {
        assertThat(Mapping.error("value").asMailAddress()).isEmpty();
    }

    @Test
    public void asMailAddressShouldReturnEmptyForRegex() {
        assertThat(Mapping.regex("value").asMailAddress()).isEmpty();
    }

    @Test
    public void asMailAddressShouldReturnEmptyForDomain() {
        assertThat(Mapping.domain(Domain.of("value")).asMailAddress()).isEmpty();
    }

    @Test
    public void asMailAddressShouldReturnMappingValueForForward() throws Exception {
        assertThat(Mapping.forward("value@domain").asMailAddress())
            .contains(new MailAddress("value@domain"));
    }

}
