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

import jakarta.mail.internet.AddressException;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class DropListEntryTest {

    @Test
    void shouldRespectEqualsContract() {
        EqualsVerifier.forClass(DropListEntry.class)
            .verify();
    }

    @Test
    void shouldThrowOnWhenBuilderIsEmpty() {
        DropListEntry.Builder builder = DropListEntry.builder();

        assertThatThrownBy(builder::build)
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowOnWithoutOwnerScope() {
        DropListEntry.Builder builder = DropListEntry.builder()
            .denyDomain(Domain.of("denied.com"));

        assertThatThrownBy(builder::build)
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowOnNullUserOwner() {
        DropListEntry.Builder builder = DropListEntry.builder();

        assertThatThrownBy(() -> builder.userOwner(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowOnNullDomainOwner() {
        DropListEntry.Builder builder = DropListEntry.builder();

        assertThatThrownBy(() -> builder.domainOwner(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowOnWithoutDeniedEntity() {
        DropListEntry.Builder builder = DropListEntry.builder()
            .forAll();

        assertThatThrownBy(builder::build)
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowOnNullDeniedDomain() {
        DropListEntry.Builder builder = DropListEntry.builder()
            .forAll();

        assertThatThrownBy(() -> builder.denyDomain(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowOnNullDeniedMailAddress() {
        DropListEntry.Builder builder = DropListEntry.builder()
            .forAll();

        assertThatThrownBy(() -> builder.denyAddress(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldGlobalOwnerScopeBeSetWhenForAllIsCalled() {
        DropListEntry dropListEntry = DropListEntry.builder()
            .forAll()
            .denyDomain(Domain.of("denied.com"))
            .build();

        assertThat(dropListEntry.getOwnerScope()).isEqualTo(OwnerScope.GLOBAL);
    }

    @Test
    void shouldEmptyOwnerBeSetWhenForAllIsCalled() throws AddressException {
        DropListEntry dropListEntry = DropListEntry.builder()
            .forAll()
            .denyAddress(new MailAddress("denied@example.com"))
            .build();

        assertThat(dropListEntry.getOwner()).isEmpty();
    }

    @Test
    void shouldReturnDropListEntryAsString() throws AddressException {
        String expectedString = "DropListEntry{ownerScope=USER, owner=owner@example.com, deniedType=DOMAIN, deniedEntity=denied.com}";
        DropListEntry dropListEntry = DropListEntry.builder()
            .userOwner(new MailAddress("owner@example.com"))
            .denyDomain(Domain.of("denied.com"))
            .build();

        assertThat(dropListEntry).hasToString(expectedString);
    }

    @Test
    void shouldReturnDropListEntryAsStringWithoutOwnerWhenScopeGlobal() {
        String expectedString = "DropListEntry{ownerScope=GLOBAL, deniedType=DOMAIN, deniedEntity=denied.com}";
        DropListEntry dropListEntry = DropListEntry.builder()
            .forAll()
            .denyDomain(Domain.of("denied.com"))
            .build();

        assertThat(dropListEntry).hasToString(expectedString);
    }

}