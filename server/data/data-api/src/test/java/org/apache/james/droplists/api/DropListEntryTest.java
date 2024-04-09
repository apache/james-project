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
package org.apache.james.droplists.api;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class DropListEntryTest {

    private final String LONG_ENTITY = StringUtils.repeat('x', 254);

    @Test
    void shouldRespectEqualsContract() {
        EqualsVerifier.forClass(DropListEntry.class)
            .verify();
    }

    @Test
    void shouldThrowOnWithoutOwner() {
        DropListEntry.Builder builder = DropListEntry.builder()
            .deniedEntityType(DeniedEntityType.DOMAIN)
            .deniedEntity("deniedEntity");

        assertThatThrownBy(builder::build)
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowOnEmptyOwner() {
        DropListEntry.Builder builder = DropListEntry.builder()
            .owner("")
            .deniedEntityType(DeniedEntityType.DOMAIN)
            .deniedEntity("deniedEntity");

        assertThatThrownBy(builder::build)
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowOnBlankOwner() {
        DropListEntry.Builder builder = DropListEntry.builder()
            .owner(" ")
            .deniedEntityType(DeniedEntityType.DOMAIN)
            .deniedEntity("deniedEntity");

        assertThatThrownBy(builder::build)
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowOnNullOwner() {
        DropListEntry.Builder builder = DropListEntry.builder();

        assertThatThrownBy(() -> builder.owner(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowOnWithoutDeniedEntity() {
        DropListEntry.Builder builder = DropListEntry.builder()
            .owner("owner")
            .deniedEntityType(DeniedEntityType.DOMAIN)
            .deniedEntity("");

        assertThatThrownBy(builder::build)
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowOnEmptyDeniedEntity() {
        DropListEntry.Builder builder = DropListEntry.builder()
            .owner("owner")
            .deniedEntityType(DeniedEntityType.DOMAIN)
            .deniedEntity("");

        assertThatThrownBy(builder::build)
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowOnBlankDeniedEntity() {
        DropListEntry.Builder builder = DropListEntry.builder()
            .owner("owner")
            .deniedEntityType(DeniedEntityType.DOMAIN)
            .deniedEntity(" ");

        assertThatThrownBy(builder::build)
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowOnNullDeniedEntity() {
        DropListEntry.Builder builder = DropListEntry.builder()
            .owner("owner")
            .deniedEntityType(DeniedEntityType.DOMAIN)
            .deniedEntity(" ");

        assertThatThrownBy(() -> builder.deniedEntity(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldDefaultGlobalOwnerScopeWhenNotSpecified() {
        DropListEntry dropListEntry = DropListEntry.builder()
            .owner("owner")
            .deniedEntityType(DeniedEntityType.DOMAIN)
            .deniedEntity("entity")
            .build();

        assertThat(dropListEntry.getOwnerScope()).isEqualTo(OwnerScope.GLOBAL);
    }

    @Test
    void shouldDefaultGlobalOwnerScopeOnNull() {
        DropListEntry dropListEntry = DropListEntry.builder()
            .ownerScope(null)
            .owner("owner")
            .deniedEntityType(DeniedEntityType.DOMAIN)
            .deniedEntity("entity")
            .build();

        assertThat(dropListEntry.getOwnerScope()).isEqualTo(OwnerScope.GLOBAL);
    }

    @Test
    void shouldThrowOnNullDeniedEntityType() {
        DropListEntry.Builder builder = DropListEntry.builder();

        assertThatThrownBy(() -> builder.deniedEntityType(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowWhenDeniedEntityTooLong() {
        DropListEntry.Builder builder = DropListEntry.builder()
            .owner("owner")
            .deniedEntityType(DeniedEntityType.DOMAIN)
            .deniedEntity(LONG_ENTITY);

        assertThatThrownBy(builder::build)
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowWhenOwnerEntityTooLong() {
        DropListEntry.Builder builder = DropListEntry.builder()
            .owner(LONG_ENTITY)
            .deniedEntityType(DeniedEntityType.DOMAIN)
            .deniedEntity("deniedEntity");

        assertThatThrownBy(builder::build)
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldReturnDropListEntryAsString() {
        String expectedString = "DropListEntry{ownerScope=GLOBAL, owner=owner, deniedType=DOMAIN, deniedEntity=entity}";
        DropListEntry dropListEntry = DropListEntry.builder()
            .owner("owner")
            .deniedEntityType(DeniedEntityType.DOMAIN)
            .deniedEntity("entity")
            .build();

        assertThat(expectedString).isEqualTo(dropListEntry.toString());
    }

}