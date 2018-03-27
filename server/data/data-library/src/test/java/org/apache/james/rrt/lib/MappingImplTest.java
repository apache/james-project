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
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class MappingImplTest {

    @Test
    public void beanShouldRespectBeanContract() {
        EqualsVerifier.forClass(MappingImpl.class)
            .verify();
    }

    @Test(expected = NullPointerException.class)
    public void addressFactoryMethodShouldThrowOnNull() {
        assertThat(MappingImpl.address(null));
    }
    
    @Test(expected = NullPointerException.class)
    public void regexFactoryMethodShouldThrowOnNull() {
        assertThat(MappingImpl.regex(null));
    }
    
    @Test(expected = NullPointerException.class)
    public void domainFactoryMethodShouldThrowOnNull() {
        assertThat(MappingImpl.domain(null));
    }
    
    
    @Test(expected = NullPointerException.class)
    public void errorFactoryMethodShouldThrowOnNull() {
        assertThat(MappingImpl.error(null));
    }
    
    @Test
    public void hasDomainshouldReturnTrueWhenMappingContainAtMark() {
        assertThat(MappingImpl.address("a@b").hasDomain()).isTrue();
    }
    
    @Test
    public void hasDomainshouldReturnFalseWhenMappingIsEmpty() {
        assertThat(MappingImpl.address("").hasDomain()).isFalse();
    }

    @Test
    public void hasDomainshouldReturnFalseWhenMappingIsBlank() {
        assertThat(MappingImpl.address(" ").hasDomain()).isFalse();
    }

    @Test
    public void hasDomainshouldReturnFalseWhenMappingDoesntContainAtMark() {
        assertThat(MappingImpl.address("abc").hasDomain()).isFalse();
    }
    
    @Test
    public void appendDomainShouldWorkOnValidDomain() {
        assertThat(MappingImpl.address("abc").appendDomain(Domain.of("domain"))).isEqualTo(MappingImpl.address("abc@domain"));
    }
    
    @Test
    public void appendDomainShouldWorkWhenMappingAlreadyContainsDomains() {
        assertThat(MappingImpl.address("abc@d").appendDomain(Domain.of("domain"))).isEqualTo(MappingImpl.address("abc@d@domain"));
    }
    
    @Test(expected = NullPointerException.class)
    public void appendDomainShouldThrowWhenNullDomain() {
        MappingImpl.address("abc@d").appendDomain(null);
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
    public void getAddressShouldReturnMappingValueForAddress() {
        assertThat(MappingImpl.address("value").getAddress()).isEqualTo("value");
    }

    @Test
    public void getAddressShouldThrowForError() {
        assertThatThrownBy(() -> MappingImpl.error("value").getAddress()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void getAddressShouldThrowForRegex() {
        assertThatThrownBy(() -> MappingImpl.regex("value").getAddress()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void getAddressShouldThrowForDomain() {
        assertThatThrownBy(() -> MappingImpl.domain(Domain.of("value")).getAddress()).isInstanceOf(IllegalStateException.class);
    }
}
