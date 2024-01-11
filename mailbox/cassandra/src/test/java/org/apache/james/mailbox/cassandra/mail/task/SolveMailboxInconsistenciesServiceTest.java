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

package org.apache.james.mailbox.cassandra.mail.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionDAO;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionManager;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.apache.james.backends.cassandra.versions.SchemaVersion;
import org.apache.james.core.Username;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxPathV3DAO;
import org.apache.james.mailbox.cassandra.mail.task.SolveMailboxInconsistenciesService.Context;
import org.apache.james.mailbox.cassandra.modules.CassandraAclModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxModule;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.task.Task.Result;
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class SolveMailboxInconsistenciesServiceTest {
    private static final UidValidity UID_VALIDITY_1 = UidValidity.of(145);
    private static final UidValidity UID_VALIDITY_2 = UidValidity.of(147);
    private static final Username USER = Username.of("user");
    private static final MailboxPath MAILBOX_PATH = MailboxPath.forUser(USER, "abc");
    private static final MailboxPath NEW_MAILBOX_PATH = MailboxPath.forUser(USER, "xyz");
    private static final CassandraId CASSANDRA_ID_1 = CassandraId.timeBased();
    private static final Mailbox MAILBOX = new Mailbox(MAILBOX_PATH, UID_VALIDITY_1, CASSANDRA_ID_1);
    private static final Mailbox MAILBOX_NEW_PATH = new Mailbox(NEW_MAILBOX_PATH, UID_VALIDITY_1, CASSANDRA_ID_1);
    private static final CassandraId CASSANDRA_ID_2 = CassandraId.timeBased();
    private static final Mailbox MAILBOX_2 = new Mailbox(MAILBOX_PATH, UID_VALIDITY_1, CASSANDRA_ID_2);

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(
        CassandraModule.aggregateModules(
            CassandraSchemaVersionModule.MODULE,
            CassandraMailboxModule.MODULE,
            CassandraAclModule.MODULE));


    CassandraMailboxDAO mailboxDAO;
    CassandraMailboxPathV3DAO mailboxPathV3DAO;
    CassandraSchemaVersionDAO versionDAO;
    SolveMailboxInconsistenciesService testee;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        mailboxDAO = new CassandraMailboxDAO(
            cassandra.getConf(),
            cassandra.getTypesProvider());
        mailboxPathV3DAO = new CassandraMailboxPathV3DAO(
            cassandra.getConf());
        versionDAO = new CassandraSchemaVersionDAO(cassandra.getConf());
        testee = new SolveMailboxInconsistenciesService(mailboxDAO, mailboxPathV3DAO, new CassandraSchemaVersionManager(versionDAO));

        versionDAO.updateVersion(new SchemaVersion(8)).block();
    }

    @Test
    void fixMailboxInconsistenciesShouldFailWhenIsBelowMailboxPathV2Migration() {
        versionDAO.truncateVersion().block();
        versionDAO.updateVersion(new SchemaVersion(5)).block();

        assertThatThrownBy(() -> testee.fixMailboxInconsistencies(new Context()).block())
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Schema version 8 is required in order to ensure mailboxPathV3DAO to be correctly populated, got 5");
    }

    @Test
    void fixMailboxInconsistenciesShouldNotFailWhenIsEqualToMailboxPathV2Migration() {
        versionDAO.truncateVersion().block();
        versionDAO.updateVersion(new SchemaVersion(8)).block();

        assertThatCode(() -> testee.fixMailboxInconsistencies(new Context()).block())
            .doesNotThrowAnyException();
    }

    @Test
    void fixMailboxInconsistenciesShouldNotFailWhenIsAboveMailboxPathV2Migration() {
        versionDAO.truncateVersion().block();
        versionDAO.updateVersion(new SchemaVersion(8)).block();

        assertThatCode(() -> testee.fixMailboxInconsistencies(new Context()).block())
            .doesNotThrowAnyException();
    }

    @Test
    void fixMailboxInconsistenciesShouldReturnCompletedWhenNoData() {
        assertThat(testee.fixMailboxInconsistencies(new Context()).block())
            .isEqualTo(Result.COMPLETED);
    }

    @Test
    void fixMailboxInconsistenciesShouldReturnCompletedWhenConsistentData() {
        mailboxDAO.save(MAILBOX).block();
        mailboxPathV3DAO.save(MAILBOX).block();

        assertThat(testee.fixMailboxInconsistencies(new Context()).block())
            .isEqualTo(Result.COMPLETED);
    }

    @Test
    void fixMailboxInconsistenciesShouldReturnCompletedWhenOrphanMailboxData() {
        mailboxDAO.save(MAILBOX).block();

        assertThat(testee.fixMailboxInconsistencies(new Context()).block())
            .isEqualTo(Result.COMPLETED);
    }

    @Test
    void fixMailboxInconsistenciesShouldReturnCompletedWhenOrphanPathData() {
        mailboxPathV3DAO.save(MAILBOX).block();

        assertThat(testee.fixMailboxInconsistencies(new Context()).block())
            .isEqualTo(Result.COMPLETED);
    }

    @Test
    void fixMailboxInconsistenciesShouldReturnPartialWhenDAOMisMatchOnId() {
        mailboxDAO.save(MAILBOX).block();
        mailboxPathV3DAO.save(MAILBOX_2).block();

        assertThat(testee.fixMailboxInconsistencies(new Context()).block())
            .isEqualTo(Result.PARTIAL);
    }

    @Test
    void fixMailboxInconsistenciesShouldReturnPartialWhenDAOMisMatchOnPath() {
        mailboxDAO.save(MAILBOX).block();
        mailboxPathV3DAO.save(MAILBOX_NEW_PATH).block();

        assertThat(testee.fixMailboxInconsistencies(new Context()).block())
            .isEqualTo(Result.PARTIAL);
    }

    @Test
    void fixMailboxInconsistenciesShouldNotUpdateContextWhenNoData() {
        Context context = new Context();

        testee.fixMailboxInconsistencies(context).block();

        RecursiveComparisonConfiguration recursiveComparisonConfiguration = new RecursiveComparisonConfiguration();
        recursiveComparisonConfiguration.registerComparatorForType(Comparator.comparingInt(AtomicInteger::get), AtomicInteger.class);
        recursiveComparisonConfiguration.registerComparatorForType(Comparator.comparingLong(AtomicLong::get), AtomicLong.class);
        recursiveComparisonConfiguration.registerEqualsForType((o, o2) -> o.get() == o2.get(), AtomicInteger.class);
        recursiveComparisonConfiguration.registerEqualsForType((o, o2) -> o.get() == o2.get(), AtomicLong.class);
        recursiveComparisonConfiguration.registerEqualsForType((o, o2) -> o.get() == o2.get(), AtomicBoolean.class);
        assertThat(context.snapshot())
            .usingRecursiveComparison(recursiveComparisonConfiguration)
            .isEqualTo(new Context().snapshot());
    }

    @Test
    void fixMailboxInconsistenciesShouldUpdateContextWhenConsistentData() {
        Context context = new Context();
        mailboxDAO.save(MAILBOX).block();
        mailboxPathV3DAO.save(MAILBOX).block();

        testee.fixMailboxInconsistencies(context).block();

        assertThat(context.snapshot())
            .isEqualTo(Context.builder()
                .processedMailboxEntries(1)
                .processedMailboxPathEntries(1)
                .build()
                .snapshot());
    }

    @Test
    void fixMailboxInconsistenciesShouldUpdateContextWhenOrphanMailboxData() {
        Context context = new Context();
        mailboxDAO.save(MAILBOX).block();

        testee.fixMailboxInconsistencies(context).block();

        assertThat(context.snapshot())
            .isEqualTo(Context.builder()
                .processedMailboxEntries(1)
                .processedMailboxPathEntries(1)
                .addFixedInconsistencies(MAILBOX.getMailboxId())
                .build()
                .snapshot());
    }

    @Test
    void fixMailboxInconsistenciesShouldUpdateContextWhenOrphanPathData() {
        Context context = new Context();
        mailboxPathV3DAO.save(MAILBOX).block();

        testee.fixMailboxInconsistencies(context).block();

        assertThat(context.snapshot())
            .isEqualTo(Context.builder()
                .processedMailboxPathEntries(1)
                .addFixedInconsistencies(CASSANDRA_ID_1)
                .build()
                .snapshot());
    }

    @Test
    void fixMailboxInconsistenciesShouldUpdateContextWhenDAOMisMatchOnId() {
        Context context = new Context();
        mailboxDAO.save(MAILBOX).block();
        mailboxPathV3DAO.save(MAILBOX_2).block();
        mailboxDAO.save(new Mailbox(MAILBOX_PATH, UID_VALIDITY_2, CASSANDRA_ID_2)).block();

        testee.fixMailboxInconsistencies(context).block();

        assertThat(context.snapshot())
            .isEqualTo(Context.builder()
                .processedMailboxEntries(2)
                .processedMailboxPathEntries(1)
                .addConflictingEntry(ConflictingEntry.builder()
                    .mailboxDaoEntry(MAILBOX)
                    .mailboxPathDaoEntry(MAILBOX_PATH, CASSANDRA_ID_2))
                .build()
                .snapshot());
    }

    @Test
    void fixMailboxInconsistenciesShouldUpdateContextWhenDAOMisMatchOnPath() {
        Context context = new Context();
        mailboxDAO.save(MAILBOX).block();
        mailboxPathV3DAO.save(MAILBOX_NEW_PATH).block();

        testee.fixMailboxInconsistencies(context).block();

        assertThat(context.snapshot())
            .isEqualTo(Context.builder()
                .processedMailboxEntries(1)
                .processedMailboxPathEntries(2)
                .addFixedInconsistencies(CASSANDRA_ID_1)
                .addConflictingEntry(ConflictingEntry.builder()
                    .mailboxDaoEntry(MAILBOX)
                    .mailboxPathDaoEntry(MAILBOX_NEW_PATH))
                .build()
                .snapshot());
    }

    @Test
    void fixMailboxInconsistenciesShouldNotAlterStateWhenEmpty() {
        testee.fixMailboxInconsistencies(new Context()).block();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(mailboxDAO.retrieveAllMailboxes().collectList().block()).isEmpty();
            softly.assertThat(mailboxPathV3DAO.listAll().collectList().block()).isEmpty();
        });
    }

    @Test
    void fixMailboxInconsistenciesShouldNotAlterStateWhenConsistent() {
        mailboxDAO.save(MAILBOX).block();
        mailboxPathV3DAO.save(MAILBOX).block();

        testee.fixMailboxInconsistencies(new Context()).block();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(mailboxDAO.retrieveAllMailboxes().collectList().block())
                .containsExactlyInAnyOrder(MAILBOX);
            softly.assertThat(mailboxPathV3DAO.listAll().collectList().block())
                .containsExactlyInAnyOrder(MAILBOX);
        });
    }

    @Test
    void fixMailboxInconsistenciesShouldAlterStateWhenOrphanMailbox() {
        mailboxDAO.save(MAILBOX).block();

        testee.fixMailboxInconsistencies(new Context()).block();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(mailboxDAO.retrieveAllMailboxes().collectList().block())
                .containsExactlyInAnyOrder(MAILBOX);
            softly.assertThat(mailboxPathV3DAO.listAll().collectList().block())
                .containsExactlyInAnyOrder(MAILBOX);
        });
    }

    @Test
    void fixMailboxInconsistenciesShouldAlterStateWhenOrphanMailboxPath() {
        mailboxPathV3DAO.save(MAILBOX).block();

        testee.fixMailboxInconsistencies(new Context()).block();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(mailboxDAO.retrieveAllMailboxes().collectList().block())
                .isEmpty();
            softly.assertThat(mailboxPathV3DAO.listAll().collectList().block())
                .isEmpty();
        });
    }

    @Test
    void fixMailboxInconsistenciesShouldNotAlterStateWhenLoop() {
        mailboxDAO.save(MAILBOX).block();
        Mailbox mailbox2 = new Mailbox(NEW_MAILBOX_PATH, UID_VALIDITY_2, CASSANDRA_ID_2);
        mailboxDAO.save(mailbox2).block();
        mailboxPathV3DAO.save(MAILBOX_2).block();
        mailboxPathV3DAO.save(MAILBOX_NEW_PATH).block();

        testee.fixMailboxInconsistencies(new Context()).block();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(mailboxDAO.retrieveAllMailboxes().collectList().block())
                .containsExactlyInAnyOrder(MAILBOX, mailbox2);
            softly.assertThat(mailboxPathV3DAO.listAll().collectList().block())
                .containsExactlyInAnyOrder(
                    MAILBOX_NEW_PATH,
                    MAILBOX_2);
        });
    }

    @Test
    void fixMailboxInconsistenciesShouldAlterStateWhenDaoMisMatchOnPath() {
        // Note that CASSANDRA_ID_1 becomes usable
        // However in order to avoid data loss, merging CASSANDRA_ID_1 and CASSANDRA_ID_2 is still required
        mailboxDAO.save(MAILBOX).block();
        mailboxPathV3DAO.save(MAILBOX_NEW_PATH).block();

        testee.fixMailboxInconsistencies(new Context()).block();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(mailboxDAO.retrieveAllMailboxes().collectList().block())
                .containsExactlyInAnyOrder(MAILBOX);
            softly.assertThat(mailboxPathV3DAO.listAll().collectList().block())
                .containsExactlyInAnyOrder(
                    MAILBOX_NEW_PATH,
                    MAILBOX);
        });
    }

    @Test
    void fixMailboxInconsistenciesShouldAlterStateWhenDaoMisMatchOnId() {
        mailboxDAO.save(MAILBOX).block();
        mailboxPathV3DAO.save(MAILBOX_2).block();

        testee.fixMailboxInconsistencies(new Context()).block();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(mailboxDAO.retrieveAllMailboxes().collectList().block())
                .containsExactlyInAnyOrder(MAILBOX);
            softly.assertThat(mailboxPathV3DAO.listAll().collectList().block())
                .isEmpty();
        });
    }

    @Test
    void multipleRunShouldDaoMisMatchOnId() {
        mailboxDAO.save(MAILBOX).block();
        mailboxPathV3DAO.save(MAILBOX_2).block();

        testee.fixMailboxInconsistencies(new Context()).block();
        testee.fixMailboxInconsistencies(new Context()).block();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(mailboxDAO.retrieveAllMailboxes().collectList().block())
                .containsExactlyInAnyOrder(MAILBOX);
            softly.assertThat(mailboxPathV3DAO.listAll().collectList().block())
                .containsExactlyInAnyOrder(MAILBOX);
        });
    }

    @Test
    void fixMailboxInconsistenciesShouldNotAlterStateWhenTwoEntriesWithSamePath() {
        // Both mailbox merge is required
        Mailbox mailbox2 = new Mailbox(MAILBOX_PATH, UID_VALIDITY_2, CASSANDRA_ID_2);

        mailboxDAO.save(MAILBOX).block();
        mailboxPathV3DAO.save(MAILBOX_2).block();
        mailboxDAO.save(mailbox2).block();

        testee.fixMailboxInconsistencies(new Context()).block();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(mailboxDAO.retrieveAllMailboxes().collectList().block())
                .containsExactlyInAnyOrder(MAILBOX, mailbox2);
            softly.assertThat(mailboxPathV3DAO.listAll().collectList().block())
                .containsExactlyInAnyOrder(MAILBOX_2);
        });
    }
}