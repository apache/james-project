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

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.literal;
import static org.apache.james.backends.cassandra.Scenario.Builder.awaitOn;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.Scenario.Barrier;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionDAO;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionManager;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.apache.james.backends.cassandra.versions.SchemaVersion;
import org.apache.james.eventsourcing.eventstore.cassandra.CassandraEventStore;
import org.apache.james.eventsourcing.eventstore.cassandra.CassandraEventStoreModule;
import org.apache.james.eventsourcing.eventstore.cassandra.EventStoreDao;
import org.apache.james.eventsourcing.eventstore.cassandra.JsonEventSerializer;
import org.apache.james.mailbox.cassandra.mail.eventsourcing.acl.ACLModule;
import org.apache.james.mailbox.cassandra.modules.CassandraAclModule;
import org.apache.james.mailbox.cassandra.table.CassandraACLTable;
import org.apache.james.mailbox.model.MailboxACL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraACLMapperV1Test extends CassandraACLMapperContract {
    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(
        CassandraModule.aggregateModules(CassandraAclModule.MODULE, CassandraSchemaVersionModule.MODULE, CassandraEventStoreModule.MODULE()));

    private CassandraACLMapper cassandraACLMapper;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        CassandraSchemaVersionDAO schemaVersionDAO = new CassandraSchemaVersionDAO(cassandra.getConf());
        schemaVersionDAO.truncateVersion().block();
        schemaVersionDAO.updateVersion(new SchemaVersion(9)).block();
        CassandraSchemaVersionManager versionManager = new CassandraSchemaVersionManager(schemaVersionDAO);
        CassandraACLDAOV1 aclDAOV1 = new CassandraACLDAOV1(cassandra.getConf(), CassandraConfiguration.DEFAULT_CONFIGURATION);
        CassandraACLDAOV2 aclDAOv2 = new CassandraACLDAOV2(cassandra.getConf());
        JsonEventSerializer jsonEventSerializer = JsonEventSerializer
            .forModules(ACLModule.ACL_UPDATE)
            .withoutNestedType();
        CassandraUserMailboxRightsDAO usersRightDAO = new CassandraUserMailboxRightsDAO(cassandra.getConf());
        CassandraEventStore eventStore = new CassandraEventStore(new EventStoreDao(cassandra.getConf(), jsonEventSerializer));
        cassandraACLMapper = new CassandraACLMapper(
            new CassandraACLMapper.StoreV1(usersRightDAO, aclDAOV1),
            new CassandraACLMapper.StoreV2(usersRightDAO, aclDAOv2, eventStore),
            versionManager, CassandraConfiguration.DEFAULT_CONFIGURATION);
    }

    @Override
    CassandraACLMapper cassandraACLMapper() {
        return cassandraACLMapper;
    }

    @Test
    void retrieveACLWhenInvalidInBaseShouldReturnEmptyACL(CassandraCluster cassandra) {
        cassandra.getConf().execute(
            insertInto(CassandraACLTable.TABLE_NAME)
                .value(CassandraACLTable.ID, literal(MAILBOX_ID.asUuid()))
                .value(CassandraACLTable.ACL, literal("{\"entries\":{\"bob\":invalid}}"))
                .value(CassandraACLTable.VERSION, literal(1))
                .build());

        assertThat(cassandraACLMapper.getACL(MAILBOX_ID).block()).isEqualTo(MailboxACL.EMPTY);
    }

    @Test
    void updateInvalidACLShouldBeBasedOnEmptyACL(CassandraCluster cassandra) throws Exception {
        cassandra.getConf().execute(
            insertInto(CassandraACLTable.TABLE_NAME)
                .value(CassandraACLTable.ID, literal(MAILBOX_ID.asUuid()))
                .value(CassandraACLTable.ACL, literal("{\"entries\":{\"bob\":invalid}}"))
                .value(CassandraACLTable.VERSION, literal(1))
                .build());
        MailboxACL.EntryKey key = new MailboxACL.EntryKey("bob", MailboxACL.NameType.user, false);
        MailboxACL.Rfc4314Rights rights = new MailboxACL.Rfc4314Rights(MailboxACL.Right.Read);

        cassandraACLMapper.updateACL(MAILBOX_ID, MailboxACL.command().key(key).rights(rights).asAddition()).block();

        assertThat(cassandraACLMapper.getACL(MAILBOX_ID).block()).isEqualTo(new MailboxACL().union(key, rights));
    }

    @Test
    void twoConcurrentUpdatesWhenNoACLStoredShouldReturnACLWithTwoEntries(CassandraCluster cassandra) throws Exception {
        Barrier barrier = new Barrier(2);
        cassandra.getConf()
            .registerScenario(awaitOn(barrier)
                .thenExecuteNormally()
                .times(2)
                .whenQueryStartsWith("SELECT acl,version FROM acl"));

        MailboxACL.EntryKey keyBob = new MailboxACL.EntryKey("bob", MailboxACL.NameType.user, false);
        MailboxACL.Rfc4314Rights rights = new MailboxACL.Rfc4314Rights(MailboxACL.Right.Read);
        MailboxACL.EntryKey keyAlice = new MailboxACL.EntryKey("alice", MailboxACL.NameType.user, false);
        Future<Boolean> future1 = performACLUpdateInExecutor(executor, keyBob, rights);
        Future<Boolean> future2 = performACLUpdateInExecutor(executor, keyAlice, rights);

        barrier.awaitCaller();
        barrier.releaseCaller();

        awaitAll(future1, future2);

        assertThat(cassandraACLMapper.getACL(MAILBOX_ID).block())
            .isEqualTo(new MailboxACL().union(keyBob, rights).union(keyAlice, rights));
    }

    @Test
    void twoConcurrentUpdatesWhenStoredShouldReturnACLWithTwoEntries(CassandraCluster cassandra) throws Exception {
        MailboxACL.EntryKey keyBenwa = new MailboxACL.EntryKey("benwa", MailboxACL.NameType.user, false);
        MailboxACL.Rfc4314Rights rights = new MailboxACL.Rfc4314Rights(MailboxACL.Right.Read);
        cassandraACLMapper.updateACL(MAILBOX_ID, MailboxACL.command().key(keyBenwa).rights(rights).asAddition()).block();

        Barrier barrier = new Barrier(2);
        cassandra.getConf()
            .registerScenario(awaitOn(barrier)
                .thenExecuteNormally()
                .times(2)
                .whenQueryStartsWith("SELECT acl,version FROM acl"));

        MailboxACL.EntryKey keyBob = new MailboxACL.EntryKey("bob", MailboxACL.NameType.user, false);
        MailboxACL.EntryKey keyAlice = new MailboxACL.EntryKey("alice", MailboxACL.NameType.user, false);
        Future<Boolean> future1 = performACLUpdateInExecutor(executor, keyBob, rights);
        Future<Boolean> future2 = performACLUpdateInExecutor(executor, keyAlice, rights);

        barrier.awaitCaller();
        barrier.releaseCaller();

        awaitAll(future1, future2);

        assertThat(cassandraACLMapper.getACL(MAILBOX_ID).block())
            .isEqualTo(new MailboxACL().union(keyBob, rights).union(keyAlice, rights).union(keyBenwa, rights));
    }

    private void awaitAll(Future<?>... futures) 
            throws InterruptedException, ExecutionException, TimeoutException {
        for (Future<?> future : futures) {
            future.get(10L, TimeUnit.SECONDS);
        }
    }

    private Future<Boolean> performACLUpdateInExecutor(ExecutorService executor, MailboxACL.EntryKey key, MailboxACL.Rfc4314Rights rights) {
        return executor.submit(() -> {
            cassandraACLMapper.updateACL(MAILBOX_ID, MailboxACL.command().key(key).rights(rights).asAddition()).block();
            return true;
        });
    }

}
