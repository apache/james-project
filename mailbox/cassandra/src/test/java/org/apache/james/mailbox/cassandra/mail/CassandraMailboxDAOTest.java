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

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.literal;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.update;
import static com.datastax.oss.driver.api.querybuilder.relation.Relation.column;
import static org.apache.james.backends.cassandra.Scenario.Builder.awaitOn;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.Scenario.Barrier;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.apache.james.core.Username;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.modules.CassandraAclModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxModule;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.UidValidity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraMailboxDAOTest {
    private static final UidValidity UID_VALIDITY_1 = UidValidity.of(145);
    private static final UidValidity UID_VALIDITY_2 = UidValidity.of(147);
    private static final Username USER = Username.of("user");
    private static final MailboxPath NEW_MAILBOX_PATH = MailboxPath.forUser(USER, "xyz");
    private static CassandraId CASSANDRA_ID_1 = CassandraId.timeBased();

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(
        CassandraModule.aggregateModules(
            CassandraSchemaVersionModule.MODULE,
            CassandraMailboxModule.MODULE,
            CassandraAclModule.MODULE));


    private CassandraMailboxDAO testee;
    private Mailbox mailbox1;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        testee = new CassandraMailboxDAO(
            cassandra.getConf(),
            cassandra.getTypesProvider());

        mailbox1 = new Mailbox(MailboxPath.forUser(USER, "abcd"),
            UID_VALIDITY_1,
            CASSANDRA_ID_1);
    }

    @Test
    void retrieveMailboxShouldReturnEmptyWhenNone() {
        assertThat(testee.retrieveMailbox(CASSANDRA_ID_1).blockOptional())
            .isEmpty();
    }

    @Test
    void saveShouldAddAMailbox() {
        testee.save(mailbox1).block();

        Optional<Mailbox> readMailbox = testee.retrieveMailbox(CASSANDRA_ID_1)
            .blockOptional();
        assertThat(readMailbox.isPresent()).isTrue();
        assertThat(readMailbox.get()).isEqualToComparingFieldByField(mailbox1);
    }

    @Test
    void retrieveMailboxShouldSanitizeInvalidUidValidityUponRead(CassandraCluster cassandra) {
        testee.save(mailbox1).block();

        // Hack to insert a faulty value
        cassandra.getConf().execute(update("mailbox")
            .setColumn("uidvalidity", literal(-12))
            .where(column("id").isEqualTo(literal(CASSANDRA_ID_1.asUuid())))
            .build());

        Optional<Mailbox> readMailbox = testee.retrieveMailbox(CASSANDRA_ID_1)
            .blockOptional();
        assertThat(readMailbox).isPresent()
            .hasValueSatisfying(mailbox -> assertThat(mailbox.getUidValidity().isValid()).isTrue());
    }

    @Test
    void retrieveAllShouldSanitizeInvalidUidValidityUponRead(CassandraCluster cassandra) {
        testee.save(mailbox1).block();

        // Hack to insert a faulty value
        cassandra.getConf().execute(update("mailbox")
            .setColumn("uidvalidity", literal(-12))
            .where(column("id").isEqualTo(literal(CASSANDRA_ID_1.asUuid())))
            .build());

        List<Mailbox> readMailbox = testee.retrieveAllMailboxes().collectList().block();
        assertThat(readMailbox).hasSize(1)
            .allSatisfy(mailbox -> assertThat(mailbox.getUidValidity().isValid()).isTrue());
    }

    @Disabled("Expected concurrency issue in the absence of performance expensive LightWeight transaction" +
        "As the Uid validity is updated only when equal to 0 (1 chance out of 4 billion) the benefits of LWT don't" +
        "outweigh the costs")
    @Test
    void retrieveMailboxShouldNotBeSubjectToDataRaceUponUidValiditySanitizing(CassandraCluster cassandra) throws Exception {
        testee.save(mailbox1).block();

        // Hack to insert a faulty value
        cassandra.getConf().execute(update("mailbox")
            .setColumn("uidvalidity", literal(-12))
            .where(column("id").isEqualTo(literal(CASSANDRA_ID_1.asUuid())))
            .build());

        Barrier barrier = new Barrier(2);
        cassandra.getConf().registerScenario(awaitOn(barrier)
            .thenExecuteNormally()
            .times(2)
            .whenQueryStartsWith("UPDATE mailbox SET"));

        CompletableFuture<Mailbox> readMailbox1 = testee.retrieveMailbox(CASSANDRA_ID_1)
            .toFuture();
        CompletableFuture<Mailbox> readMailbox2 = testee.retrieveMailbox(CASSANDRA_ID_1)
            .toFuture();

        barrier.awaitCaller();
        barrier.releaseCaller();

        assertThat(readMailbox1.get().getUidValidity())
            .isEqualTo(readMailbox2.get().getUidValidity());
    }

    @Test
    void saveShouldOverride() {
        testee.save(mailbox1).block();

        Mailbox mailbox2 = createMailbox(CASSANDRA_ID_1);
        testee.save(mailbox2).block();

        Optional<Mailbox> readMailbox = testee.retrieveMailbox(CASSANDRA_ID_1)
            .blockOptional();
        assertThat(readMailbox.isPresent()).isTrue();
        assertThat(readMailbox.get()).isEqualToComparingFieldByField(mailbox2);
    }

    @Test
    void retrieveAllMailboxesShouldBeEmptyByDefault() {
        List<Mailbox> mailboxes = testee.retrieveAllMailboxes()
            .collectList()
            .block();

        assertThat(mailboxes).isEmpty();
    }

    @Test
    void retrieveAllMailboxesShouldReturnSingleMailbox() {
        testee.save(mailbox1).block();

        List<Mailbox> mailboxes = testee.retrieveAllMailboxes()
            .collectList()
            .block();

        assertThat(mailboxes).containsOnly(mailbox1);
    }

    @Test
    void retrieveAllMailboxesShouldReturnMultiMailboxes() {
        Mailbox mailbox2 = createMailbox(CassandraId.timeBased());

        testee.save(mailbox1).block();
        testee.save(mailbox2).block();

        List<Mailbox> mailboxes = testee.retrieveAllMailboxes()
            .collectList()
            .block();

        assertThat(mailboxes).containsOnly(mailbox1, mailbox2);
    }

    @Test
    void deleteShouldNotFailWhenMailboxIsAbsent() {
        testee.delete(CASSANDRA_ID_1).block();
    }

    @Test
    void deleteShouldRemoveExistingMailbox() {
        testee.save(mailbox1).block();

        testee.delete(CASSANDRA_ID_1).block();

        assertThat(testee.retrieveMailbox(CASSANDRA_ID_1).blockOptional())
            .isEmpty();
    }

    @Test
    void updateShouldNotFailWhenMailboxIsAbsent() {
        testee.updatePath(CASSANDRA_ID_1, NEW_MAILBOX_PATH).block();
    }

    @Test
    void updateShouldChangeMailboxPath() {
        testee.save(mailbox1).block();

        testee.updatePath(CASSANDRA_ID_1, NEW_MAILBOX_PATH).block();

        mailbox1.setNamespace(NEW_MAILBOX_PATH.getNamespace());
        mailbox1.setUser(NEW_MAILBOX_PATH.getUser());
        mailbox1.setName(NEW_MAILBOX_PATH.getName());
        Optional<Mailbox> readMailbox = testee.retrieveMailbox(CASSANDRA_ID_1).blockOptional();
        assertThat(readMailbox.isPresent()).isTrue();
        assertThat(readMailbox.get()).isEqualToComparingFieldByField(mailbox1);
    }

    private Mailbox createMailbox(MailboxId mailboxId) {
        return new Mailbox(MailboxPath.forUser(USER, "defg"), UID_VALIDITY_2, mailboxId);
    }
}
