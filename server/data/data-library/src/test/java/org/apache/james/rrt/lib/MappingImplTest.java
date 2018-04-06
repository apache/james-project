/*
 *   Licensed to the Apache Software Foundation (ASF) under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ASF licenses this file
 *   to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 *
 */

package org.apache.james.rrt.lib;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class MappingImplTest {

    @Test
    public void beanShouldRespectBeanContract() {
        EqualsVerifier.forClass(MappingImpl.class)
            .verify();
    }

    @Test
    public void addressFactoryMethodShouldThrowOnNull() {
        assertThatThrownBy(() -> MappingImpl.address(null))
            .isInstanceOf(NullPointerException.class);
    }
    
    @Test
    public void regexFactoryMethodShouldThrowOnNull() {
        assertThatThrownBy(() -> MappingImpl.regex(null))
            .isInstanceOf(NullPointerException.class);
    }
    
    @Test
    public void domainFactoryMethodShouldThrowOnNull() {
        assertThatThrownBy(() -> MappingImpl.domain(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void errorFactoryMethodShouldThrowOnNull() {
        assertThatThrownBy(() -> MappingImpl.error(null))
            .isInstanceOf(NullPointerException.class);
    }
    
    @Test
    public void forwardFactoryMethodShouldThrowOnNull() {
        assertThatThrownBy(() -> MappingImpl.forward(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void groupFactoryMethodShouldThrowOnNull() {
        assertThatThrownBy(() -> MappingImpl.group(null))
            .isInstanceOf(NullPointerException.class);
    }
    
    @Test
    public void hasDomainShouldReturnTrueWhenMappingContainAtMark() {
        assertThat(MappingImpl.address("a@b").hasDomain()).isTrue();
    }
    
    @Test
    public void hasDomainShouldReturnFalseWhenMappingIsEmpty() {
        assertThat(MappingImpl.address("").hasDomain()).isFalse();
    }

    @Test
    public void hasDomainShouldReturnFalseWhenMappingIsBlank() {
        assertThat(MappingImpl.address(" ").hasDomain()).isFalse();
    }

    @Test
    public void hasDomainShouldReturnFalseWhenMappingDoesntContainAtMark() {
        assertThat(MappingImpl.address("abc").hasDomain()).isFalse();
    }
    
    @Test
    public void appendDefaultDomainShouldWorkOnValidDomain() {
        assertThat(MappingImpl.address("abc").appendDomainIfNone(() -> Domain.of("domain"))).isEqualTo(MappingImpl.address("abc@domain"));
    }
    
    @Test
    public void appendDefaultDomainShouldNotAddDomainWhenMappingAlreadyContainsDomains() {
        assertThat(MappingImpl.address("abc@d").appendDomainIfNone(() -> Domain.of("domain"))).isEqualTo(MappingImpl.address("abc@d"));
    }
    
    @Test
    public void appendDomainShouldThrowWhenNullDomain() {
        assertThatThrownBy(() -> MappingImpl.address("abc@d").appendDomainIfNone(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    public void appendDomainFromThrowingSupplierIfNoneShouldWorkOnValidDomain() throws RecipientRewriteTableException {
        assertThat(MappingImpl.address("abc").appendDomainFromThrowingSupplierIfNone(() -> Domain.of("domain")))
            .isEqualTo(MappingImpl.address("abc@domain"));
    }

    @Test
    public void appendDomainFromThrowingSupplierIfNoneShouldNotAddDomainWhenMappingAlreadyContainsDomains() throws RecipientRewriteTableException {
        assertThat(MappingImpl.address("abc@d").appendDomainFromThrowingSupplierIfNone(() -> Domain.of("domain")))
            .isEqualTo(MappingImpl.address("abc@d"));
    }

    @Test
    public void appendDomainFromThrowingSupplierIfNoneShouldThrowWhenNullDomain() {
        assertThatThrownBy(() -> MappingImpl.address("abc@d").appendDomainFromThrowingSupplierIfNone(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void appendDomainFromThrowingSupplierIfNoneShouldNotCatchRecipientRewriteTableException() {
        Mapping.ThrowingDomainSupplier supplier = () -> {
            throw new RecipientRewriteTableException("message");
        };
        assertThatThrownBy(() -> MappingImpl.address("abc").appendDomainFromThrowingSupplierIfNone(supplier))
            .isInstanceOf(RecipientRewriteTableException.class);
    }

    @Test
    public void getTypeShouldReturnAddressWhenNoPrefix() {
        assertThat(MappingImpl.address("abc").getType()).isEqualTo(Mapping.Type.Address);
    }

    @Test
    public void getTypeShouldReturnAddressWhenEmpty() {
        assertThat(MappingImpl.address("").getType()).isEqualTo(Mapping.Type.Address);
    }
    
    @Test
    public void getTypeShouldReturnRegexWhenRegexPrefix() {
        assertThat(MappingImpl.regex("abc").getType()).isEqualTo(Mapping.Type.Regex);
    }

    @Test
    public void getTypeShouldReturnErrorWhenErrorPrefix() {
        assertThat(MappingImpl.error("abc").getType()).isEqualTo(Mapping.Type.Error);
    }

    @Test
    public void getTypeShouldReturnDomainWhenDomainPrefix() {
        assertThat(MappingImpl.domain(Domain.of("abc")).getType()).isEqualTo(Mapping.Type.Domain);
    }

    @Test
    public void getTypeShouldReturnForwardWhenForwardPrefix() {
        assertThat(MappingImpl.forward("abc").getType()).isEqualTo(Mapping.Type.Forward);
    }

    @Test
    public void getTypeShouldReturnGroupWhenGroupPrefix() {
        assertThat(MappingImpl.group("abc").getType()).isEqualTo(Mapping.Type.Group);
    }
    
    @Test(expected = IllegalStateException.class)
    public void getErrorMessageShouldThrowWhenMappingIsNotAnError() {
        MappingImpl.domain(Domain.of("toto")).getErrorMessage();
    }
    
    @Test
    public void getErrorMessageShouldReturnMessageWhenErrorWithMessage() {
        assertThat(MappingImpl.error("toto").getErrorMessage()).isEqualTo("toto");
    }

    @Test
    public void getErrorMessageShouldReturnWhenErrorWithoutMessage() {
        assertThat(MappingImpl.error("").getErrorMessage()).isEqualTo("");
    }

    @Test
    public void asMailAddressShouldReturnMappingValueForAddress() throws Exception {
        assertThat(MappingImpl.address("value@domain").asMailAddress())
            .contains(new MailAddress("value@domain"));
    }

    @Test
    public void asMailAddressShouldReturnEmptyOnInvalidAddress() {
        assertThat(MappingImpl.address("value").asMailAddress())
            .isEmpty();
    }

    @Test
    public void asMailAddressShouldReturnEmptyForError() {
        assertThat(MappingImpl.error("value").asMailAddress()).isEmpty();
    }

    @Test
    public void asMailAddressShouldReturnEmptyForRegex() {
        assertThat(MappingImpl.regex("value").asMailAddress()).isEmpty();
    }

    @Test
    public void asMailAddressShouldReturnEmptyForDomain() {
        assertThat(MappingImpl.domain(Domain.of("value")).asMailAddress()).isEmpty();
    }

    @Test
    public void asMailAddressShouldReturnMappingValueForForward() throws Exception {
        assertThat(MappingImpl.forward("value@domain").asMailAddress())
            .contains(new MailAddress("value@domain"));
    }
}
