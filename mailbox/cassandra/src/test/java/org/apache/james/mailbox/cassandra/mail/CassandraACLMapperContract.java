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
package org.apache.james.mailbox.cassandra.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.util.concurrent.NamedThreadFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

abstract class CassandraACLMapperContract {
    static final CassandraId MAILBOX_ID = CassandraId.of(UUID.fromString("464765a0-e4e7-11e4-aba4-710c1de3782b"));

    ExecutorService executor;

    abstract CassandraACLMapper cassandraACLMapper();

    @BeforeEach
    void setUpExecutor() {
        ThreadFactory threadFactory = NamedThreadFactory.withClassName(getClass());
        executor = Executors.newFixedThreadPool(2, threadFactory);
    }


    @AfterEach
    void tearDownExecutor() {
        executor.shutdownNow();
    }

    @Test
    void retrieveACLWhenNoACLStoredShouldReturnEmptyACL() {
        assertThat(cassandraACLMapper().getACL(MAILBOX_ID).blockOptional()).isEmpty();
    }

    @Test
    void deleteShouldRemoveACL() {
        MailboxACL.EntryKey key = new MailboxACL.EntryKey("bob", MailboxACL.NameType.user, false);
        MailboxACL.Rfc4314Rights rights = new MailboxACL.Rfc4314Rights(MailboxACL.Right.Read);

        cassandraACLMapper().updateACL(MAILBOX_ID,
            MailboxACL.command().key(key).rights(rights).asAddition());

        cassandraACLMapper().delete(MAILBOX_ID).block();

        assertThat(cassandraACLMapper().getACL(MAILBOX_ID).blockOptional()).isEmpty();
    }

    @Test
    void deleteShouldNotThrowWhenDoesNotExist() {
        assertThatCode(() -> cassandraACLMapper().delete(MAILBOX_ID).block())
            .doesNotThrowAnyException();
    }

    @Test
    void addACLWhenNoneStoredShouldReturnUpdatedACL() throws Exception {
        MailboxACL.EntryKey key = new MailboxACL.EntryKey("bob", MailboxACL.NameType.user, false);
        MailboxACL.Rfc4314Rights rights = new MailboxACL.Rfc4314Rights(MailboxACL.Right.Read);

        cassandraACLMapper().updateACL(MAILBOX_ID,
            MailboxACL.command().key(key).rights(rights).asAddition()).block();

        assertThat(cassandraACLMapper().getACL(MAILBOX_ID).block())
            .isEqualTo(new MailboxACL().union(key, rights));
    }

    @Test
    void modifyACLWhenStoredShouldReturnUpdatedACL() throws MailboxException {
        MailboxACL.EntryKey keyBob = new MailboxACL.EntryKey("bob", MailboxACL.NameType.user, false);
        MailboxACL.Rfc4314Rights rights = new MailboxACL.Rfc4314Rights(MailboxACL.Right.Read);

        cassandraACLMapper().updateACL(MAILBOX_ID, MailboxACL.command().key(keyBob).rights(rights).asAddition()).block();
        MailboxACL.EntryKey keyAlice = new MailboxACL.EntryKey("alice", MailboxACL.NameType.user, false);
        cassandraACLMapper().updateACL(MAILBOX_ID, MailboxACL.command().key(keyAlice).rights(rights).asAddition()).block();

        assertThat(cassandraACLMapper().getACL(MAILBOX_ID).block())
            .isEqualTo(new MailboxACL().union(keyBob, rights).union(keyAlice, rights));
    }

    @Test
    void removeWhenStoredShouldReturnUpdatedACL() throws MailboxException {
        MailboxACL.EntryKey key = new MailboxACL.EntryKey("bob", MailboxACL.NameType.user, false);
        MailboxACL.Rfc4314Rights rights = new MailboxACL.Rfc4314Rights(MailboxACL.Right.Read);

        cassandraACLMapper().updateACL(MAILBOX_ID, MailboxACL.command().key(key).rights(rights).asAddition()).block();
        cassandraACLMapper().updateACL(MAILBOX_ID, MailboxACL.command().key(key).rights(rights).asRemoval()).block();

        assertThat(cassandraACLMapper().getACL(MAILBOX_ID).blockOptional().orElse(MailboxACL.EMPTY)).isEqualTo(MailboxACL.EMPTY);
    }

    @Test
    void replaceForSingleKeyWithNullRightsWhenSingleKeyStoredShouldReturnEmptyACL() throws MailboxException {
        MailboxACL.EntryKey key = new MailboxACL.EntryKey("bob", MailboxACL.NameType.user, false);
        MailboxACL.Rfc4314Rights rights = new MailboxACL.Rfc4314Rights(MailboxACL.Right.Read);

        cassandraACLMapper().updateACL(MAILBOX_ID, MailboxACL.command().key(key).rights(rights).asAddition()).block();
        cassandraACLMapper().updateACL(MAILBOX_ID, MailboxACL.command().key(key).noRights().asReplacement()).block();

        assertThat(cassandraACLMapper().getACL(MAILBOX_ID).blockOptional().orElse(MailboxACL.EMPTY)).isEqualTo(MailboxACL.EMPTY);
    }

    @Test
    void replaceWhenNotStoredShouldUpdateACLEntry() throws MailboxException {
        MailboxACL.EntryKey key = new MailboxACL.EntryKey("bob", MailboxACL.NameType.user, false);
        MailboxACL.Rfc4314Rights rights = new MailboxACL.Rfc4314Rights(MailboxACL.Right.Read);

        cassandraACLMapper().updateACL(MAILBOX_ID, MailboxACL.command().key(key).rights(rights).asReplacement()).block();

        assertThat(cassandraACLMapper().getACL(MAILBOX_ID).block()).isEqualTo(new MailboxACL().union(key, rights));
    }
}
