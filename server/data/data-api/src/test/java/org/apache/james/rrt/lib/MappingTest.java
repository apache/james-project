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
    void hasPrefixShouldReturnTrueWhenRegex() {
        boolean hasPrefix = Mapping.Type.hasPrefix(Type.Regex.asPrefix() + "myRegex");
        assertThat(hasPrefix).isTrue();
    }

    @Test
    void hasPrefixShouldReturnTrueWhenDomain() {
        boolean hasPrefix = Mapping.Type.hasPrefix(Type.Domain.asPrefix() + "myDomain");
        assertThat(hasPrefix).isTrue();
    }

    @Test
    void hasPrefixShouldReturnTrueWhenError() {
        boolean hasPrefix = Mapping.Type.hasPrefix(Type.Error.asPrefix() + "myError");
        assertThat(hasPrefix).isTrue();
    }

    @Test
    void hasPrefixShouldReturnTrueWhenForward() {
        boolean hasPrefix = Mapping.Type.hasPrefix(Type.Forward.asPrefix() + "myForward");
        assertThat(hasPrefix).isTrue();
    }

    @Test
    void hasPrefixShouldReturnTrueWhenAlias() {
        boolean hasPrefix = Mapping.Type.hasPrefix(Type.Alias.asPrefix() + "myAlias");
        assertThat(hasPrefix).isTrue();
    }

    @Test
    void hasPrefixShouldReturnFalseWhenAddress() {
        boolean hasPrefix = Mapping.Type.hasPrefix(Type.Address.asPrefix() + "myAddress");
        assertThat(hasPrefix).isFalse();
    }

    @Test
    void detectTypeShouldReturnAddressWhenAddressPrefix() {
        assertThat(Mapping.detectType(Type.Address.asPrefix() + "mapping"))
            .isEqualTo(Type.Address);
    }

    @Test
    void detectTypeShouldReturnRegexWhenRegexPrefix() {
        assertThat(Mapping.detectType(Type.Regex.asPrefix() + "mapping"))
            .isEqualTo(Type.Regex);
    }

    @Test
    void detectTypeShouldReturnErrorWhenErrorPrefix() {
        assertThat(Mapping.detectType(Type.Error.asPrefix() + "mapping"))
            .isEqualTo(Type.Error);
    }

    @Test
    void detectTypeShouldReturnDomainWhenDomainPrefix() {
        assertThat(Mapping.detectType(Type.Domain.asPrefix() + "mapping"))
            .isEqualTo(Type.Domain);
    }

    @Test
    void detectTypeShouldReturnForwardWhenForwardPrefix() {
        assertThat(Mapping.detectType(Type.Forward.asPrefix() + "mapping"))
            .isEqualTo(Type.Forward);
    }

    @Test
    void detectTypeShouldReturnAliasWhenAliasPrefix() {
        assertThat(Mapping.detectType(Type.Alias.asPrefix() + "mapping"))
            .isEqualTo(Type.Alias);
    }

    @Test
    void withoutPrefixShouldRemoveAddressPrefix() {
        assertThat(Type.Address.withoutPrefix(Type.Address.asPrefix() + "mapping"))
            .isEqualTo("mapping");
    }

    @Test
    void withoutPrefixShouldDoNothingWhenAddressAndNoPrefix() {
        assertThat(Type.Address.withoutPrefix("mapping"))
            .isEqualTo("mapping");
    }

    @Test
    void withoutPrefixShouldRemoveDomainPrefix() {
        assertThat(Type.Domain.withoutPrefix(Type.Domain.asPrefix() + "mapping"))
            .isEqualTo("mapping");
    }

    @Test
    void withoutPrefixShouldRemoveErrorPrefix() {
        assertThat(Type.Error.withoutPrefix(Type.Error.asPrefix() + "mapping"))
            .isEqualTo("mapping");
    }

    @Test
    void withoutPrefixShouldRemoveRegexPrefix() {
        assertThat(Type.Regex.withoutPrefix(Type.Regex.asPrefix() + "mapping"))
            .isEqualTo("mapping");
    }

    @Test
    void withoutPrefixShouldRemoveForwardPrefix() {
        assertThat(Type.Forward.withoutPrefix(Type.Forward.asPrefix() + "mapping"))
            .isEqualTo("mapping");
    }

    @Test
    void withoutPrefixShouldRemoveAliasPrefix() {
        assertThat(Type.Alias.withoutPrefix(Type.Alias.asPrefix() + "mapping"))
            .isEqualTo("mapping");
    }

    @Test
    void withoutPrefixShouldThrowOnBadPrefix() {
        assertThatThrownBy(() -> Type.Regex.withoutPrefix(Type.Domain.asPrefix() + "mapping"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void withoutPrefixShouldThrowWhenNoPrefix() {
        assertThatThrownBy(() -> Type.Regex.withoutPrefix("mapping"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void withoutPrefixShouldThrowWhenNoPrefixOnForwardType() {
        assertThatThrownBy(() -> Type.Forward.withoutPrefix("mapping"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void beanShouldRespectBeanContract() {
        EqualsVerifier.forClass(Mapping.Impl.class)
            .withOnlyTheseFields("type", "mapping")
            .verify();
    }

    @Test
    void addressFactoryMethodShouldThrowOnNull() {
        assertThatThrownBy(() -> Mapping.address(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void regexFactoryMethodShouldThrowOnNull() {
        assertThatThrownBy(() -> Mapping.regex(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void domainFactoryMethodShouldThrowOnNull() {
        assertThatThrownBy(() -> Mapping.domain(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void domainAliasFactoryMethodShouldThrowOnNull() {
        assertThatThrownBy(() -> Mapping.domainAlias(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void errorFactoryMethodShouldThrowOnNull() {
        assertThatThrownBy(() -> Mapping.error(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void forwardFactoryMethodShouldThrowOnNull() {
        assertThatThrownBy(() -> Mapping.forward(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void groupFactoryMethodShouldThrowOnNull() {
        assertThatThrownBy(() -> Mapping.group(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void aliasFactoryMethodShouldThrowOnNull() {
        assertThatThrownBy(() -> Mapping.alias(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void hasDomainShouldReturnTrueWhenMappingContainAtMark() {
        assertThat(Mapping.address("a@b").hasDomain()).isTrue();
    }

    @Test
    void hasDomainShouldReturnFalseWhenMappingIsEmpty() {
        assertThat(Mapping.address("").hasDomain()).isFalse();
    }

    @Test
    void hasDomainShouldReturnFalseWhenMappingIsBlank() {
        assertThat(Mapping.address(" ").hasDomain()).isFalse();
    }

    @Test
    void hasDomainShouldReturnFalseWhenMappingDoesntContainAtMark() {
        assertThat(Mapping.address("abc").hasDomain()).isFalse();
    }

    @Test
    void appendDefaultDomainShouldWorkOnValidDomain() {
        assertThat(Mapping.address("abc").appendDomainIfNone(() -> Domain.of("domain"))).isEqualTo(Mapping.address("abc@domain"));
    }

    @Test
    void appendDefaultDomainShouldNotAddDomainWhenMappingAlreadyContainsDomains() {
        assertThat(Mapping.address("abc@d").appendDomainIfNone(() -> Domain.of("domain"))).isEqualTo(Mapping.address("abc@d"));
    }

    @Test
    void appendDomainShouldThrowWhenNullDomain() {
        assertThatThrownBy(() -> Mapping.address("abc@d").appendDomainIfNone(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void getTypeShouldReturnAddressWhenNoPrefix() {
        assertThat(Mapping.address("abc").getType()).isEqualTo(Mapping.Type.Address);
    }

    @Test
    void getTypeShouldReturnAddressWhenEmpty() {
        assertThat(Mapping.address("").getType()).isEqualTo(Mapping.Type.Address);
    }

    @Test
    void getTypeShouldReturnRegexWhenRegexPrefix() {
        assertThat(Mapping.regex("abc").getType()).isEqualTo(Mapping.Type.Regex);
    }

    @Test
    void getTypeShouldReturnErrorWhenErrorPrefix() {
        assertThat(Mapping.error("abc").getType()).isEqualTo(Mapping.Type.Error);
    }

    @Test
    void getTypeShouldReturnDomainWhenDomainPrefix() {
        assertThat(Mapping.domain(Domain.of("abc")).getType()).isEqualTo(Mapping.Type.Domain);
    }

    @Test
    void getTypeShouldReturnDomainAliasWhenDomainAliasPrefix() {
        assertThat(Mapping.domainAlias(Domain.of("abc")).getType()).isEqualTo(Type.DomainAlias);
    }

    @Test
    void getTypeShouldReturnForwardWhenForwardPrefix() {
        assertThat(Mapping.forward("abc").getType()).isEqualTo(Mapping.Type.Forward);
    }

    @Test
    void getTypeShouldReturnGroupWhenGroupPrefix() {
        assertThat(Mapping.group("abc").getType()).isEqualTo(Mapping.Type.Group);
    }

    @Test
    void getTypeShouldReturnAliasWhenAliasPrefix() {
        assertThat(Mapping.alias("abc").getType()).isEqualTo(Mapping.Type.Alias);
    }

    @Test
    void getErrorMessageShouldThrowWhenMappingIsNotAnError() {
        assertThatThrownBy(() -> Mapping.domain(Domain.of("toto")).getErrorMessage())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void getErrorMessageShouldReturnMessageWhenErrorWithMessage() {
        assertThat(Mapping.error("toto").getErrorMessage()).isEqualTo("toto");
    }

    @Test
    void getErrorMessageShouldReturnWhenErrorWithoutMessage() {
        assertThat(Mapping.error("").getErrorMessage()).isEqualTo("");
    }

    @Test
    void asMailAddressShouldReturnMappingValueForAddress() throws Exception {
        assertThat(Mapping.address("value@domain").asMailAddress())
            .contains(new MailAddress("value@domain"));
    }

    @Test
    void asMailAddressShouldReturnEmptyOnInvalidAddress() {
        assertThat(Mapping.address("value").asMailAddress())
            .isEmpty();
    }

    @Test
    void asMailAddressShouldReturnEmptyForError() {
        assertThat(Mapping.error("value").asMailAddress()).isEmpty();
    }

    @Test
    void asMailAddressShouldReturnEmptyForRegex() {
        assertThat(Mapping.regex("value").asMailAddress()).isEmpty();
    }

    @Test
    void asMailAddressShouldReturnEmptyForDomain() {
        assertThat(Mapping.domain(Domain.of("value")).asMailAddress()).isEmpty();
    }

    @Test
    void asMailAddressShouldReturnEmptyForDomainAlias() {
        assertThat(Mapping.domainAlias(Domain.of("value")).asMailAddress()).isEmpty();
    }

    @Test
    void asMailAddressShouldReturnMappingValueForForward() throws Exception {
        assertThat(Mapping.forward("value@domain").asMailAddress())
            .contains(new MailAddress("value@domain"));
    }

    @Test
    void asMailAddressShouldReturnMappingValueForAlias() throws Exception {
        assertThat(Mapping.alias("value@domain").asMailAddress())
            .contains(new MailAddress("value@domain"));
    }

}
