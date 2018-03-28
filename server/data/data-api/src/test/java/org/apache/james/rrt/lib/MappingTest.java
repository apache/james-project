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

import org.apache.james.rrt.lib.Mapping.Type;
import org.junit.jupiter.api.Test;

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
    public void withoutPrefixShouldThrowOnBadPrefix() {
        assertThatThrownBy(() -> Type.Regex.withoutPrefix(Type.Domain.asPrefix() + "mapping"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void withoutPrefixShouldThrowWhenNoPrefix() {
        assertThatThrownBy(() -> Type.Regex.withoutPrefix("mapping"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
