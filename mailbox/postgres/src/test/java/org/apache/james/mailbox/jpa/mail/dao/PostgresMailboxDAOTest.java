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

package org.apache.james.mailbox.jpa.mail.dao;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.apache.james.core.Username;
import org.apache.james.mailbox.jpa.user.PostgresMailboxId;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.UidValidity;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Mono;

public abstract class PostgresMailboxDAOTest {
    private static final char DELIMITER = '.';
    private static final Mailbox EMPTY_MAILBOX = null;

    abstract PostgresMailboxDAO postgresMailboxDAO();

    @Test
    void createShouldRunSuccessfully() {
        MailboxPath mailboxPath = new MailboxPath("AAA", Username.of("alice"), "HOME");

        MailboxId mailboxId = postgresMailboxDAO().create(mailboxPath, UidValidity.of(1L)).block().getMailboxId();

        Mailbox expected = new Mailbox(mailboxPath, UidValidity.of(1L), mailboxId);
        Mailbox actual = postgresMailboxDAO().findMailboxById(mailboxId).block();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void createShouldReturnExceptionWhenMailboxAlreadyExist() {
        MailboxPath mailboxPath = new MailboxPath("AAA", Username.of("alice"), "HOME");

        postgresMailboxDAO().create(mailboxPath, UidValidity.of(1L)).block().getMailboxId();

        String message = postgresMailboxDAO().create(mailboxPath, UidValidity.of(2L))
            .map(mailbox -> "")
            .onErrorResume(throwable -> Mono.just(throwable.getMessage()))
            .block();

        assertThat(message).isEqualTo("Mailbox with name=HOME already exists.");
    }

    @Test
    void renameShouldRunSuccessfully() {
        MailboxPath mailboxPath = new MailboxPath("AAA", Username.of("alice"), "HOME");
        MailboxId mailboxId = postgresMailboxDAO().create(mailboxPath, UidValidity.of(1L)).block().getMailboxId();

        MailboxPath mailboxPathTwo = new MailboxPath("BBB", Username.of("bob"), "COMPANY");
        postgresMailboxDAO().rename(new Mailbox(mailboxPathTwo, UidValidity.of(1L), mailboxId)).block();

        Mailbox expected = new Mailbox(mailboxPathTwo, UidValidity.of(1L), mailboxId);
        Mailbox actual = postgresMailboxDAO().findMailboxById(mailboxId).block();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void deleteShouldRunSuccessfully() {
        MailboxPath mailboxPath = new MailboxPath("AAA", Username.of("alice"), "HOME");

        MailboxId mailboxId = postgresMailboxDAO().create(mailboxPath, UidValidity.of(1L)).block().getMailboxId();
        postgresMailboxDAO().delete(mailboxId).block();

        Mailbox actual = postgresMailboxDAO().findMailboxById(mailboxId).block();

        assertThat(actual).isEqualTo(EMPTY_MAILBOX);
    }

    @Test
    void findMailboxByIdShouldReturnNullWhenNoMailboxIsFound() {
        Mailbox actual = postgresMailboxDAO().findMailboxById(PostgresMailboxId.of(1L)).block();

        assertThat(actual).isEqualTo(EMPTY_MAILBOX);
    }

    @Test
    void findMailboxByPathShouldRunSuccessfully() {
        MailboxPath mailboxPath = new MailboxPath("AAA", Username.of("alice"), "HOME");

        MailboxId mailboxId = postgresMailboxDAO().create(mailboxPath, UidValidity.of(1L)).block().getMailboxId();

        Mailbox expected = new Mailbox(mailboxPath, UidValidity.of(1L), mailboxId);
        Mailbox actual = postgresMailboxDAO().findMailboxByPath(mailboxPath).block();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void findMailboxByPathShouldReturnNullWhenNoMailboxIsFound() {
        MailboxPath mailboxPath = new MailboxPath("AAA", Username.of("alice"), "HOME");
        Mailbox actual = postgresMailboxDAO().findMailboxByPath(mailboxPath).block();

        assertThat(actual).isEqualTo(EMPTY_MAILBOX);
    }

    @Test
    void getAllShouldRunSuccessfully() {
        MailboxPath mailboxPathOne = new MailboxPath("AAA", Username.of("alice"), "HOME");
        MailboxId mailboxIdOne = postgresMailboxDAO().create(mailboxPathOne, UidValidity.of(1L)).block().getMailboxId();

        MailboxPath mailboxPathTwo = new MailboxPath("BBB", Username.of("bob"), "COMPANY");
        MailboxId mailboxIdTwO = postgresMailboxDAO().create(mailboxPathTwo, UidValidity.of(2L)).block().getMailboxId();

        Set<Mailbox> expected = ImmutableSet.of(new Mailbox(mailboxPathOne, UidValidity.of(1L), mailboxIdOne),
            new Mailbox(mailboxPathTwo, UidValidity.of(2L), mailboxIdTwO));
        Set<Mailbox> actual = postgresMailboxDAO().getAll().collect(ImmutableSet.toImmutableSet()).block();

        assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void hasChildrenShouldReturnTrue() {
        MailboxPath mailboxPath = new MailboxPath("AAA", Username.of("alice"), "HOME.ONE");
        MailboxId mailboxId = postgresMailboxDAO().create(mailboxPath, UidValidity.of(1L)).block().getMailboxId();

        Mailbox mailbox = new Mailbox(new MailboxPath("AAA", Username.of("alice"), "HOME"), UidValidity.of(1L), mailboxId);

        assertThat(postgresMailboxDAO().hasChildren(mailbox, DELIMITER).block()).isTrue();
    }

    @Test
    void hasChildrenShouldReturnFalse() {
        MailboxPath mailboxPath = new MailboxPath("AAA", Username.of("alice"), "HOME");
        MailboxId mailboxId = postgresMailboxDAO().create(mailboxPath, UidValidity.of(1L)).block().getMailboxId();

        Mailbox mailbox = new Mailbox(new MailboxPath("AAA", Username.of("alice"), "HOME"), UidValidity.of(1L), mailboxId);

        assertThat(postgresMailboxDAO().hasChildren(mailbox, DELIMITER).block()).isFalse();
    }
}
