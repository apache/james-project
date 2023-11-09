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

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.apache.james.core.Username;
import org.apache.james.mailbox.jpa.PostgresMailboxId;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.mailbox.model.search.ExactName;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.mailbox.model.search.PrefixedWildcard;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Mono;

public abstract class PostgresMailboxDAOTest {
    protected static final char DELIMITER = '.';
    protected static final Mailbox EMPTY_MAILBOX = null;
    protected static final Username ALICE = Username.of("alice");
    protected static final Username BOB = Username.of("bob");
    protected static final MailboxPath ALICE_INBOX_PATH = MailboxPath.forUser(ALICE, "INBOX");
    protected static final MailboxPath BOB_INBOX_PATH = MailboxPath.forUser(BOB, "INBOX");
    private static final MailboxPath ALICE_INBOX_WORK_PATH = MailboxPath.forUser(ALICE, "INBOX" + DELIMITER + "work");

    abstract PostgresMailboxDAO postgresMailboxDAO();

    @Test
    void createShouldRunSuccessfully() {
        MailboxId mailboxId = postgresMailboxDAO().create(ALICE_INBOX_PATH, UidValidity.of(1L)).block().getMailboxId();

        Mailbox expected = new Mailbox(ALICE_INBOX_PATH, UidValidity.of(1L), mailboxId);
        Mailbox actual = postgresMailboxDAO().findMailboxById(mailboxId).block();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void createShouldReturnExceptionWhenMailboxAlreadyExist() {
        postgresMailboxDAO().create(ALICE_INBOX_PATH, UidValidity.of(1L)).block();

        String message = postgresMailboxDAO().create(ALICE_INBOX_PATH, UidValidity.of(2L))
            .map(mailbox -> "")
            .onErrorResume(throwable -> Mono.just(throwable.getMessage()))
            .block();

        assertThat(message).isEqualTo("Mailbox with name=" + ALICE_INBOX_PATH.getName() + " already exists.");
    }

    @Test
    void renameShouldRunSuccessfully() {
        MailboxId mailboxId = postgresMailboxDAO().create(ALICE_INBOX_PATH, UidValidity.of(1L)).block().getMailboxId();

        MailboxPath newMailboxPath = new MailboxPath(ALICE_INBOX_PATH.getNamespace(), ALICE_INBOX_PATH.getUser(), "ENBOX");
        postgresMailboxDAO().rename(new Mailbox(newMailboxPath, UidValidity.of(1L), mailboxId)).block();

        Mailbox expected = new Mailbox(newMailboxPath, UidValidity.of(1L), mailboxId);
        Mailbox actual = postgresMailboxDAO().findMailboxById(mailboxId).block();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void renameShouldUpdateOnyOneRecord() {
        MailboxId aliceMailboxId = postgresMailboxDAO().create(ALICE_INBOX_PATH, UidValidity.of(1L)).block().getMailboxId();
        MailboxId bobMailboxId = postgresMailboxDAO().create(BOB_INBOX_PATH, UidValidity.of(2L)).block().getMailboxId();

        MailboxPath newMailboxPath = new MailboxPath(ALICE_INBOX_PATH.getNamespace(), ALICE_INBOX_PATH.getUser(), "ENBOX");
        postgresMailboxDAO().rename(new Mailbox(newMailboxPath, UidValidity.of(1L), aliceMailboxId)).block();

        Mailbox actual = postgresMailboxDAO().findMailboxById(aliceMailboxId).block();
        Mailbox actualBobMailbox = postgresMailboxDAO().findMailboxById(bobMailboxId).block();

        assertThat(actual.getName()).isEqualTo("ENBOX");
        assertThat(actualBobMailbox.getName()).isEqualTo(BOB_INBOX_PATH.getName());
    }

    @Test
    void deleteShouldRunSuccessfully() {
        MailboxId mailboxId = postgresMailboxDAO().create(ALICE_INBOX_PATH, UidValidity.of(1L)).block().getMailboxId();
        postgresMailboxDAO().delete(mailboxId).block();

        Mailbox actual = postgresMailboxDAO().findMailboxById(mailboxId).block();

        assertThat(actual).isEqualTo(EMPTY_MAILBOX);
    }

    @Test
    void findMailboxByIdShouldReturnNullWhenNoMailboxIsFound() {
        Mailbox actual = postgresMailboxDAO().findMailboxById(PostgresMailboxId.generate()).block();

        assertThat(actual).isEqualTo(EMPTY_MAILBOX);
    }

    @Test
    void findMailboxByPathShouldRunSuccessfully() {
        MailboxId mailboxId = postgresMailboxDAO().create(ALICE_INBOX_PATH, UidValidity.of(1L)).block().getMailboxId();

        Mailbox expected = new Mailbox(ALICE_INBOX_PATH, UidValidity.of(1L), mailboxId);
        Mailbox actual = postgresMailboxDAO().findMailboxByPath(ALICE_INBOX_PATH).block();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void findMailboxByPathShouldReturnNullWhenNoMailboxIsFound() {
        Mailbox actual = postgresMailboxDAO().findMailboxByPath(ALICE_INBOX_PATH).block();

        assertThat(actual).isEqualTo(EMPTY_MAILBOX);
    }

    @Test
    void findMailboxWithPathLikeShouldReturnNonEmptyResultWhenPrefixedWildcardIsUsedAndConditionIsMatched() {
        MailboxId mailboxId = postgresMailboxDAO().create(ALICE_INBOX_WORK_PATH, UidValidity.of(1L)).block().getMailboxId();

        MailboxQuery.UserBound mailboxQuery = MailboxQuery.builder()
            .userAndNamespaceFrom(ALICE_INBOX_WORK_PATH)
            .expression(new PrefixedWildcard("IN"))
            .build()
            .asUserBound();

        List<Mailbox> mailboxes = postgresMailboxDAO().findMailboxWithPathLike(mailboxQuery)
            .collectList().block();

        Mailbox expected = new Mailbox(ALICE_INBOX_WORK_PATH, UidValidity.of(1L), mailboxId);

        assertThat(mailboxes).containsOnly(expected);
    }

    @Test
    void findMailboxWithPathLikeShouldReturnEmptyResultWhenPrefixedWildcardIsUsedAndConditionIsNotMatched() {
        postgresMailboxDAO().create(ALICE_INBOX_WORK_PATH, UidValidity.of(1L)).block();

        MailboxQuery.UserBound mailboxQuery = MailboxQuery.builder()
            .userAndNamespaceFrom(ALICE_INBOX_WORK_PATH)
            .expression(new PrefixedWildcard("IMBOX"))
            .build()
            .asUserBound();

        List<Mailbox> mailboxes = postgresMailboxDAO().findMailboxWithPathLike(mailboxQuery)
            .collectList().block();

        assertThat(mailboxes).isEmpty();
    }

    @Test
    void findMailboxWithPathLikeShouldReturnNonEmptyResultWhenExactNameIsUsedAndConditionIsMatched() {
        MailboxId mailboxId = postgresMailboxDAO().create(ALICE_INBOX_WORK_PATH, UidValidity.of(1L)).block().getMailboxId();

        MailboxQuery.UserBound mailboxQuery = MailboxQuery.builder()
            .userAndNamespaceFrom(ALICE_INBOX_WORK_PATH)
            .expression(new ExactName(ALICE_INBOX_WORK_PATH.getName()))
            .build()
            .asUserBound();

        List<Mailbox> mailboxes = postgresMailboxDAO().findMailboxWithPathLike(mailboxQuery)
            .collectList().block();

        Mailbox expected = new Mailbox(ALICE_INBOX_WORK_PATH, UidValidity.of(1L), mailboxId);

        assertThat(mailboxes).containsOnly(expected);
    }

    @Test
    void findMailboxWithPathLikeShouldReturnEmptyResultWhenExactNameIsUsedAndConditionIsNotMatched() {
        postgresMailboxDAO().create(ALICE_INBOX_WORK_PATH, UidValidity.of(1L)).block();

        MailboxQuery.UserBound mailboxQuery = MailboxQuery.builder()
            .userAndNamespaceFrom(ALICE_INBOX_WORK_PATH)
            .expression(new ExactName("IN"))
            .build()
            .asUserBound();

        List<Mailbox> mailboxes = postgresMailboxDAO().findMailboxWithPathLike(mailboxQuery)
            .collectList().block();

        assertThat(mailboxes).isEmpty();
    }

    @Test
    void getAllShouldRunSuccessfully() {
        MailboxId aliceMailboxId = postgresMailboxDAO().create(ALICE_INBOX_PATH, UidValidity.of(1L)).block().getMailboxId();
        MailboxId bobMailboxId = postgresMailboxDAO().create(BOB_INBOX_PATH, UidValidity.of(2L)).block().getMailboxId();

        Set<Mailbox> expected = ImmutableSet.of(new Mailbox(ALICE_INBOX_PATH, UidValidity.of(1L), aliceMailboxId),
            new Mailbox(BOB_INBOX_PATH, UidValidity.of(2L), bobMailboxId));
        Set<Mailbox> actual = postgresMailboxDAO().getAll().collect(ImmutableSet.toImmutableSet()).block();

        assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void hasChildrenShouldReturnTrue() {
        MailboxId mailboxId = postgresMailboxDAO().create(ALICE_INBOX_WORK_PATH, UidValidity.of(1L)).block().getMailboxId();
        Mailbox mailbox = new Mailbox(ALICE_INBOX_PATH, UidValidity.of(1L), mailboxId);

        assertThat(postgresMailboxDAO().hasChildren(mailbox, DELIMITER).block()).isTrue();
    }

    @Test
    void hasChildrenShouldReturnFalse() {
        MailboxId mailboxId = postgresMailboxDAO().create(ALICE_INBOX_PATH, UidValidity.of(1L)).block().getMailboxId();
        Mailbox mailbox = new Mailbox(ALICE_INBOX_PATH, UidValidity.of(1L), mailboxId);

        assertThat(postgresMailboxDAO().hasChildren(mailbox, DELIMITER).block()).isFalse();
    }
}
