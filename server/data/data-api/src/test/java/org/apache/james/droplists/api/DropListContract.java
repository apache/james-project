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
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.util.Objects;

import jakarta.mail.internet.AddressException;

import org.apache.james.core.MailAddress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface DropListContract {

    DropList dropList();

    @Test
    default void shouldAddEntry() throws AddressException {
        DropListEntry dropListEntry = DropListEntry.builder()
            .owner("global_scope@example.com")
            .deniedEntity("denied@denied.com")
            .deniedEntityType(DeniedEntityType.ADDRESS)
            .build();

        Mono<Void> result = dropList().add(dropListEntry);

        assertThat(1).isEqualTo(Objects.requireNonNull(dropList().list(GLOBAL, dropListEntry.getOwner()).collectList().block()).size());
        assertThat(Mono.empty()).isEqualTo(result);
    }

    @Test
    default void shouldRemoveEntry() throws AddressException {
        DropListEntry dropListEntry = DropListEntry.builder()
            .owner("global_scope@example.com")
            .deniedEntity("denied@denied.com")
            .deniedEntityType(DeniedEntityType.ADDRESS)
            .build();

        dropList().add(dropListEntry);

        Mono<Void> result = dropList().remove(dropListEntry);

        assertThat(0).isEqualTo(Objects.requireNonNull(dropList().list(GLOBAL, dropListEntry.getOwner()).collectList().block()).size());
        assertThat(Mono.empty()).isEqualTo(result);
    }

    @ParameterizedTest(name = "{index} ownerScope: {0}, owner: {1},")
    @CsvSource(value = {
        "GLOBAL, global_scope@example.com",
        "DOMAIN, domain_scope@example.com",
        "USER, user_scope@example.com",
    })
    default void shouldGetEntryListForSpecifiedScopeAndOwner(OwnerScope ownerScope, String owner) throws AddressException {
        DropListEntry dropListEntry = DropListEntry.builder()
            .ownerScope(ownerScope)
            .owner(owner)
            .deniedEntity("denied@denied.com")
            .deniedEntityType(DeniedEntityType.ADDRESS)
            .build();
        dropList().add(dropListEntry);

        Flux<DropListEntry> result = dropList().list(ownerScope, owner);

        assertThat(1).isEqualTo(Objects.requireNonNull(result.collectList().block()).size());
    }

    @ParameterizedTest(name = "{index} ownerScope: {0}, owner: {1}, deniedEntity: {2}, deniedEntityType: {3}, senderMailAddress: {4}")
    @CsvSource(value = {
        "GLOBAL, global_scope@example.com, denied@denied.com, ADDRESS, allowed@allowed.com",
        "GLOBAL, global_scope@example.com, denied.com, DOMAIN, allowed@allowed.com",
        "DOMAIN, domain_scope@example.com, denied@denied.com, ADDRESS, allowed@allowed.com",
        "DOMAIN, domain_scope@example.com, denied.com, DOMAIN, allowed@allowed.com",
        "USER, user_scope@example.com, denied@denied.com, ADDRESS, allowed@allowed.com",
        "USER, user_scope@example.com, denied.com, DOMAIN, allowed@allowed.com",
    })
    default void shouldReturnAllowed(OwnerScope ownerScope, String owner, String deniedEntity, DeniedEntityType deniedEntityType,
                                     String senderMailAddress) throws AddressException {
        MailAddress allowedSender = new MailAddress(senderMailAddress);
        dropList().add(DropListEntry.builder()
            .owner(owner)
            .ownerScope(ownerScope)
            .deniedEntity(deniedEntity)
            .deniedEntityType(deniedEntityType)
            .build());

        Mono<DropList.Status> result = dropList().query(ownerScope, owner, allowedSender);

        assertThat(DropList.Status.ALLOWED).isEqualTo(result.block());
    }

    @ParameterizedTest(name = "{index} ownerScope: {0}, owner: {1}, deniedEntity: {2}, deniedEntityType: {3}, senderMailAddress: {4}")
    @CsvSource(value = {
        "GLOBAL, global_scope@example.com, denied@denied.com, ADDRESS, denied@denied.com",
        "GLOBAL, global_scope@example.com, denied@denied.com, ADDRESS, allowed@denied.com",
        "GLOBAL, global_scope@example.com, denied.com, DOMAIN, allowed@denied.com",
        "DOMAIN, domain_scope@example.com, denied@denied.com, ADDRESS, denied@denied.com",
        "DOMAIN, domain_scope@example.com, denied.com, DOMAIN, allowed@denied.com",
        "USER, user_scope@example.com, denied@denied.com, ADDRESS, denied@denied.com",
        "USER, user_scope@example.com, denied.com, DOMAIN, allowed@denied.com",
    })
    default void shouldReturnBlocked(OwnerScope ownerScope, String owner, String deniedEntity, DeniedEntityType deniedEntityType,
                                     String senderMailAddress) throws AddressException {
        MailAddress deniedSender = new MailAddress(senderMailAddress);
        dropList().add(DropListEntry.builder()
            .owner(owner)
            .ownerScope(ownerScope)
            .deniedEntity(deniedEntity)
            .deniedEntityType(deniedEntityType)
            .build());

        Mono<DropList.Status> result = dropList().query(ownerScope, owner, deniedSender);

        assertThat(DropList.Status.BLOCKED).isEqualTo(result.block());
    }

}
