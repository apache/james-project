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

import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.james.backends.cassandra.CassandraClusterSingleton;
import org.apache.james.mailbox.cassandra.CassandraId;
import org.apache.james.mailbox.cassandra.CassandraMailboxModule;
import org.apache.james.mailbox.cassandra.table.CassandraACLTable;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.SimpleMailboxACL;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.base.Throwables;

public class CassandraACLMapperTest {

    private CassandraACLMapper cassandraACLMapper;
    private CassandraClusterSingleton cassandra;
    private SimpleMailbox<CassandraId> mailbox;
    private int uidValidity;
    private int maxRetry;
    private ExecutorService executor;

    @Before
    public void setUp() {
        cassandra = CassandraClusterSingleton.create(new CassandraMailboxModule());
        cassandra.ensureAllTables();
        uidValidity = 10;
        mailbox = new SimpleMailbox<>(new MailboxPath("#private", "benwa@linagora.com", "INBOX"), uidValidity);
        mailbox.setMailboxId(CassandraId.of(UUID.fromString("464765a0-e4e7-11e4-aba4-710c1de3782b")));
        maxRetry = 100;
        cassandraACLMapper = new CassandraACLMapper(mailbox, cassandra.getConf(), maxRetry);
        executor = Executors.newFixedThreadPool(2);
    }

    @After
    public void tearDown() {
        cassandra.clearAllTables();
        executor.shutdownNow();
    }

    @Test(expected = IllegalArgumentException.class)
    public void creatingACLMapperWithNegativeMaxRetryShouldFail() {
        new CassandraACLMapper(mailbox, cassandra.getConf(), -1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void creatingACLMapperWithNullMaxRetryShouldFail() {
        new CassandraACLMapper(mailbox, cassandra.getConf(), 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void creatingACLMapperWithNoMailboxIdShouldFail() {
        new CassandraACLMapper(new SimpleMailbox<>(new MailboxPath("#private", "user", "name"), uidValidity), cassandra.getConf(), maxRetry);
    }

    @Test
    public void retrieveACLWhenPresentInBaseShouldReturnCorrespondingACL() throws Exception {
        cassandra.getConf().execute(
            insertInto(CassandraACLTable.TABLE_NAME)
                .value(CassandraACLTable.ID, mailbox.getMailboxId().asUuid())
                .value(CassandraACLTable.ACL, "{\"entries\":{\"bob\":64}}")
                .value(CassandraACLTable.VERSION, 1)
        );
        assertThat(cassandraACLMapper.getACL())
            .isEqualTo(
                SimpleMailboxACL.EMPTY.union(
                    new SimpleMailboxACL.SimpleMailboxACLEntryKey("bob", MailboxACL.NameType.user, false),
                    new SimpleMailboxACL.Rfc4314Rights(SimpleMailboxACL.Rfc4314Rights.r_Read_RIGHT))
            );
    }

    @Test
    public void retrieveACLWhenInvalidInBaseShouldReturnEmptyACL() throws Exception {
        cassandra.getConf().execute(
            insertInto(CassandraACLTable.TABLE_NAME)
                .value(CassandraACLTable.ID, mailbox.getMailboxId().asUuid())
                .value(CassandraACLTable.ACL, "{\"entries\":{\"bob\":invalid}}")
                .value(CassandraACLTable.VERSION, 1)
        );
        assertThat(cassandraACLMapper.getACL()).isEqualTo(SimpleMailboxACL.EMPTY);
    }

    @Test
    public void retrieveACLWhenNoACLStoredShouldReturnEmptyACL() {
        assertThat(cassandraACLMapper.getACL()).isEqualTo(SimpleMailboxACL.EMPTY);
    }

    @Test
    public void addACLWhenNoneStoredShouldReturnUpdatedACL() throws Exception {
        SimpleMailboxACL.SimpleMailboxACLEntryKey key = new SimpleMailboxACL.SimpleMailboxACLEntryKey("bob", MailboxACL.NameType.user, false);
        SimpleMailboxACL.Rfc4314Rights rights = new SimpleMailboxACL.Rfc4314Rights(new SimpleMailboxACL.SimpleMailboxACLRight('r'));
        cassandraACLMapper.updateACL(new SimpleMailboxACL.SimpleMailboxACLCommand(key, MailboxACL.EditMode.ADD, rights));
        assertThat(cassandraACLMapper.getACL()).isEqualTo(new SimpleMailboxACL().union(key, rights));
    }

    @Test
    public void modifyACLWhenStoredShouldReturnUpdatedACL() throws MailboxException {
        SimpleMailboxACL.SimpleMailboxACLEntryKey keyBob = new SimpleMailboxACL.SimpleMailboxACLEntryKey("bob", MailboxACL.NameType.user, false);
        SimpleMailboxACL.Rfc4314Rights rights = new SimpleMailboxACL.Rfc4314Rights(new SimpleMailboxACL.SimpleMailboxACLRight('r'));
        cassandraACLMapper.updateACL(new SimpleMailboxACL.SimpleMailboxACLCommand(keyBob, MailboxACL.EditMode.ADD, rights));
        SimpleMailboxACL.SimpleMailboxACLEntryKey keyAlice = new SimpleMailboxACL.SimpleMailboxACLEntryKey("alice", MailboxACL.NameType.user, false);
        cassandraACLMapper.updateACL(new SimpleMailboxACL.SimpleMailboxACLCommand(keyAlice, MailboxACL.EditMode.ADD, rights));
        assertThat(cassandraACLMapper.getACL()).isEqualTo(new SimpleMailboxACL().union(keyBob, rights).union(keyAlice, rights));
    }

    @Test
    public void removeWhenStoredShouldReturnUpdatedACL() throws MailboxException {
        SimpleMailboxACL.SimpleMailboxACLEntryKey key = new SimpleMailboxACL.SimpleMailboxACLEntryKey("bob", MailboxACL.NameType.user, false);
        SimpleMailboxACL.Rfc4314Rights rights = new SimpleMailboxACL.Rfc4314Rights(new SimpleMailboxACL.SimpleMailboxACLRight('r'));
        cassandraACLMapper.updateACL(new SimpleMailboxACL.SimpleMailboxACLCommand(key, MailboxACL.EditMode.ADD, rights));
        cassandraACLMapper.updateACL(new SimpleMailboxACL.SimpleMailboxACLCommand(key, MailboxACL.EditMode.REMOVE, rights));
        assertThat(cassandraACLMapper.getACL()).isEqualTo(SimpleMailboxACL.EMPTY);
    }

    @Test
    public void replaceForSingleKeyWithNullRightsWhenSingleKeyStoredShouldReturnEmptyACL() throws MailboxException {
        SimpleMailboxACL.SimpleMailboxACLEntryKey key = new SimpleMailboxACL.SimpleMailboxACLEntryKey("bob", MailboxACL.NameType.user, false);
        SimpleMailboxACL.Rfc4314Rights rights = new SimpleMailboxACL.Rfc4314Rights(new SimpleMailboxACL.SimpleMailboxACLRight('r'));
        cassandraACLMapper.updateACL(new SimpleMailboxACL.SimpleMailboxACLCommand(key, MailboxACL.EditMode.ADD, rights));
        cassandraACLMapper.updateACL(new SimpleMailboxACL.SimpleMailboxACLCommand(key, MailboxACL.EditMode.REPLACE, null));
        assertThat(cassandraACLMapper.getACL()).isEqualTo(SimpleMailboxACL.EMPTY);
    }

    @Test
    public void replaceWhenNotStoredShouldUpdateACLEntry() throws MailboxException {
        SimpleMailboxACL.SimpleMailboxACLEntryKey key = new SimpleMailboxACL.SimpleMailboxACLEntryKey("bob", MailboxACL.NameType.user, false);
        SimpleMailboxACL.Rfc4314Rights rights = new SimpleMailboxACL.Rfc4314Rights(new SimpleMailboxACL.SimpleMailboxACLRight('r'));
        cassandraACLMapper.updateACL(new SimpleMailboxACL.SimpleMailboxACLCommand(key, MailboxACL.EditMode.REPLACE, rights));
        assertThat(cassandraACLMapper.getACL()).isEqualTo(new SimpleMailboxACL().union(key, rights));
    }

    @Test
    public void updateInvalidACLShouldBeBasedOnEmptyACL() throws Exception {
        cassandra.getConf().execute(
            insertInto(CassandraACLTable.TABLE_NAME)
                .value(CassandraACLTable.ID, mailbox.getMailboxId().asUuid())
                .value(CassandraACLTable.ACL, "{\"entries\":{\"bob\":invalid}}")
                .value(CassandraACLTable.VERSION, 1)
        );
        SimpleMailboxACL.SimpleMailboxACLEntryKey key = new SimpleMailboxACL.SimpleMailboxACLEntryKey("bob", MailboxACL.NameType.user, false);
        SimpleMailboxACL.Rfc4314Rights rights = new SimpleMailboxACL.Rfc4314Rights(new SimpleMailboxACL.SimpleMailboxACLRight('r'));
        cassandraACLMapper.updateACL(new SimpleMailboxACL.SimpleMailboxACLCommand(key, MailboxACL.EditMode.ADD, rights));
        assertThat(cassandraACLMapper.getACL()).isEqualTo(new SimpleMailboxACL().union(key, rights));
    }

    @Test
    public void twoConcurrentUpdatesWhenNoACEStoredShouldReturnACEWithTwoEntries() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(2);
        SimpleMailboxACL.SimpleMailboxACLEntryKey keyBob = new SimpleMailboxACL.SimpleMailboxACLEntryKey("bob", MailboxACL.NameType.user, false);
        SimpleMailboxACL.Rfc4314Rights rights = new SimpleMailboxACL.Rfc4314Rights(new SimpleMailboxACL.SimpleMailboxACLRight('r'));
        SimpleMailboxACL.SimpleMailboxACLEntryKey keyAlice = new SimpleMailboxACL.SimpleMailboxACLEntryKey("alice", MailboxACL.NameType.user, false);
        Future<Boolean> future1 = performACLUpdateInExecutor(executor, keyBob, rights, countDownLatch::countDown);
        Future<Boolean> future2 = performACLUpdateInExecutor(executor, keyAlice, rights, countDownLatch::countDown);
        awaitAll(future1, future2);
        assertThat(cassandraACLMapper.getACL()).isEqualTo(new SimpleMailboxACL().union(keyBob, rights).union(keyAlice, rights));
    }

    @Test
    public void twoConcurrentUpdatesWhenStoredShouldReturnACEWithTwoEntries() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(2);
        SimpleMailboxACL.SimpleMailboxACLEntryKey keyBenwa = new SimpleMailboxACL.SimpleMailboxACLEntryKey("benwa", MailboxACL.NameType.user, false);
        SimpleMailboxACL.Rfc4314Rights rights = new SimpleMailboxACL.Rfc4314Rights(new SimpleMailboxACL.SimpleMailboxACLRight('r'));
        cassandraACLMapper.updateACL(new SimpleMailboxACL.SimpleMailboxACLCommand(keyBenwa, MailboxACL.EditMode.ADD, rights));
        SimpleMailboxACL.SimpleMailboxACLEntryKey keyBob = new SimpleMailboxACL.SimpleMailboxACLEntryKey("bob", MailboxACL.NameType.user, false);
        SimpleMailboxACL.SimpleMailboxACLEntryKey keyAlice = new SimpleMailboxACL.SimpleMailboxACLEntryKey("alice", MailboxACL.NameType.user, false);
        Future<Boolean> future1 = performACLUpdateInExecutor(executor, keyBob, rights, countDownLatch::countDown);
        Future<Boolean> future2 = performACLUpdateInExecutor(executor, keyAlice, rights, countDownLatch::countDown);
        awaitAll(future1, future2);
        assertThat(cassandraACLMapper.getACL()).isEqualTo(new SimpleMailboxACL().union(keyBob, rights).union(keyAlice, rights).union(keyBenwa, rights));
    }

    private void awaitAll(Future<?>... futures) 
            throws InterruptedException, ExecutionException, TimeoutException {
        for (Future<?> future : futures) {
            future.get(10l, TimeUnit.SECONDS);
        }
    }

    private Future<Boolean> performACLUpdateInExecutor(ExecutorService executor, SimpleMailboxACL.SimpleMailboxACLEntryKey key, SimpleMailboxACL.Rfc4314Rights rights, CassandraACLMapper.CodeInjector runnable) {
        return executor.submit(() -> {
            CassandraACLMapper aclMapper = new CassandraACLMapper(mailbox, cassandra.getConf(), maxRetry, runnable);
            try {
                aclMapper.updateACL(new SimpleMailboxACL.SimpleMailboxACLCommand(key, MailboxACL.EditMode.ADD, rights));
            } catch (MailboxException exception) {
                throw Throwables.propagate(exception);
            }
            return true;
        });
    }

}
