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

import org.apache.james.rrt.api.RecipientRewriteTable;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MappingImplTest {

    @Test(expected=NullPointerException.class)
    public void factoryMethodShouldThrowOnNull() {
        assertThat(MappingImpl.of(null));
    }
    
    @Test
    public void hasDomainshouldReturnTrueWhenMappingContainAtMark() {
        assertThat(MappingImpl.of("a@b").hasDomain()).isTrue();
    }
    
    @Test
    public void hasDomainshouldReturnFalseWhenMappingIsEmpty() {
        assertThat(MappingImpl.of("").hasDomain()).isFalse();
    }

    @Test
    public void hasDomainshouldReturnFalseWhenMappingIsBlank() {
        assertThat(MappingImpl.of(" ").hasDomain()).isFalse();
    }

    @Test
    public void hasDomainshouldReturnFalseWhenMappingDoesntContainAtMark() {
        assertThat(MappingImpl.of("abc").hasDomain()).isFalse();
    }
    
    @Test
    public void appendDomainShouldWorkOnValidDomain() {
        assertThat(MappingImpl.of("abc").appendDomain("domain")).isEqualTo(MappingImpl.of("abc@domain"));
    }
    
    @Test
    public void appendDomainShouldWorkWhenMappingAlreadyContainsDomains() {
        assertThat(MappingImpl.of("abc@d").appendDomain("domain")).isEqualTo(MappingImpl.of("abc@d@domain"));
    }
    
    @Test(expected=NullPointerException.class)
    public void appendDomainShouldThrowWhenNullDomain() {
        MappingImpl.of("abc@d").appendDomain(null);
    }
    
    @Test
    public void appendDomainShouldWorkWhenEmptyDomain() {
        assertThat(MappingImpl.of("abc").appendDomain("")).isEqualTo(MappingImpl.of("abc@"));
    }

    @Test
    public void getTypeShouldReturnAddressWhenNoPrefix() {
        assertThat(MappingImpl.of("abc").getType()).isEqualTo(Mapping.Type.Address);
    }

    @Test
    public void getTypeShouldReturnAddressWhenEmpty() {
        assertThat(MappingImpl.of("").getType()).isEqualTo(Mapping.Type.Address);
    }
    
    @Test
    public void getTypeShouldReturnRegexWhenRegexPrefix() {
        assertThat(MappingImpl.of(RecipientRewriteTable.REGEX_PREFIX + "abc").getType()).isEqualTo(Mapping.Type.Regex);
    }

    @Test
    public void getTypeShouldReturnErrorWhenErrorPrefix() {
        assertThat(MappingImpl.of(RecipientRewriteTable.ERROR_PREFIX + "abc").getType()).isEqualTo(Mapping.Type.Error);
    }

    @Test
    public void getTypeShouldReturnDomainWhenDomainPrefix() {
        assertThat(MappingImpl.of(RecipientRewriteTable.ALIASDOMAIN_PREFIX + "abc").getType()).isEqualTo(Mapping.Type.Domain);
    }
}
