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

import static org.apache.james.droplists.api.OwnerScope.GLOBAL;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.util.stream.Stream;

import jakarta.mail.internet.AddressException;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface DropListContract {

    DropList dropList();

    @Test
    default void shouldAddEntry() throws AddressException {
        DropListEntry dropListEntry = DropListEntry.builder()
            .forAll()
            .denyAddress(new MailAddress("denied@denied.com"))
            .build();

        Mono<Void> result = dropList().add(dropListEntry);

        assertThat(dropList().list(GLOBAL, dropListEntry.getOwner()).collectList().block().size()).isEqualTo(1);
        assertThat(result).isEqualTo(Mono.empty());
    }

    @Test
    default void shouldRemoveEntry() throws AddressException {
        DropListEntry dropListEntry = DropListEntry.builder()
            .forAll()
            .denyAddress(new MailAddress("denied@denied.com"))
            .build();

        dropList().add(dropListEntry);

        Mono<Void> result = dropList().remove(dropListEntry);

        assertThat(dropList().list(GLOBAL, dropListEntry.getOwner()).collectList().block().size()).isZero();
        assertThat(result).isEqualTo(Mono.empty());
    }

    @Test
    default void shouldThrowWhenAddOnNullDropListEntry() {
        assertThatThrownBy(() -> dropList().add(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    default void shouldThrowWhenRemoveOnNullDropListEntry() {
        assertThatThrownBy(() -> dropList().remove(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    default void shouldThrowWhenListOnNullScope() {
        assertThatThrownBy(() -> dropList().list(null, "owner"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    default void shouldThrowWhenListOnNullOwner() {
        assertThatThrownBy(() -> dropList().list(GLOBAL, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    default void shouldThrowWhenQueryOnNullScope() {
        assertThatThrownBy(() -> dropList().query(null, "owner", new MailAddress("sender@example.com")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    default void shouldThrowWhenQueryOnNullOwner() {
        assertThatThrownBy(() -> dropList().query(GLOBAL, null, new MailAddress("sender@example.com")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    default void shouldThrowWhenQueryOnNullSender() {
        assertThatThrownBy(() -> dropList().query(GLOBAL, "owner", null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "{index} {0}")
    @MethodSource("provideParametersForGetEntryListTest")
    default void shouldGetEntryListForSpecifiedScopeAndOwner(DropListEntry dropListEntry) {
        dropList().add(dropListEntry);

        Flux<DropListEntry> result = dropList().list(dropListEntry.getOwnerScope(), dropListEntry.getOwner());

        assertThat(result.collectList().block().size()).isEqualTo(1);
    }


    @ParameterizedTest(name = "{index} {0}")
    @MethodSource("provideParametersForReturnAllowedTest")
    default void shouldReturnAllowed(DropListEntry dropListEntry, MailAddress senderMailAddress) {
        dropList().add(dropListEntry);

        Mono<DropList.Status> result = dropList().query(dropListEntry.getOwnerScope(), dropListEntry.getOwner(), senderMailAddress);

        assertThat(result.block()).isEqualTo(DropList.Status.ALLOWED);
    }

    @ParameterizedTest(name = "{index} {0}")
    @MethodSource("provideParametersForReturnBlockedTest")
    default void shouldReturnBlocked(DropListEntry dropListEntry, MailAddress senderMailAddress) {
        dropList().add(dropListEntry);

        Mono<DropList.Status> result = dropList().query(dropListEntry.getOwnerScope(), dropListEntry.getOwner(), senderMailAddress);

        assertThat(result.block()).isEqualTo(DropList.Status.BLOCKED);
    }

    static Stream<DropListEntry> getDropListTestEntries() throws AddressException {
        return Stream.of(
            DropListEntry.builder()
                .forAll()
                .denyAddress(new MailAddress("denied@denied.com"))
                .build(),
            DropListEntry.builder()
                .forAll()
                .denyDomain(Domain.of("denied.com"))
                .build(),
            DropListEntry.builder()
                .domainOwner(Domain.of("example.com"))
                .denyAddress(new MailAddress("denied@denied.com"))
                .build(),
            DropListEntry.builder()
                .domainOwner(Domain.of("example.com"))
                .denyDomain(Domain.of("denied.com"))
                .build(),
            DropListEntry.builder()
                .userOwner(new MailAddress("owner@example.com"))
                .denyAddress(new MailAddress("denied@denied.com"))
                .build(),
            DropListEntry.builder()
                .userOwner(new MailAddress("owner@example.com"))
                .denyDomain(Domain.of("denied.com"))
                .build());
    }

    static Stream<Arguments> provideParametersForGetEntryListTest() throws AddressException {
        return getDropListTestEntries().map(Arguments::of);
    }

    static Stream<Arguments> provideParametersForReturnAllowedTest() throws AddressException {
        MailAddress allowedSenderAddress = new MailAddress("allowed@allowed.com");
        return getDropListTestEntries().map(dropListEntry -> Arguments.of(dropListEntry, allowedSenderAddress));
    }

    static Stream<Arguments> provideParametersForReturnBlockedTest() throws AddressException {

        MailAddress deniedSenderAddress = new MailAddress("denied@denied.com");
        MailAddress deniedSenderDomain = new MailAddress("allowed@denied.com");
        return getDropListTestEntries().map(dropListEntry ->
            dropListEntry.getDeniedEntityType().equals(DeniedEntityType.DOMAIN) ?
                Arguments.of(dropListEntry, deniedSenderDomain) :
                Arguments.of(dropListEntry, deniedSenderAddress));
    }
}
