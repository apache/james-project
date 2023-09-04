/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.mailbox.cassandra.mail;

import static org.apache.james.backends.cassandra.Scenario.Builder.fail;
import static org.apache.james.mailbox.model.MailboxAssertingTool.softly;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.IntStream;

import org.apache.commons.lang3.StringUtils;
import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.Scenario;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionDAO;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionManager;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.apache.james.backends.cassandra.versions.SchemaVersion;
import org.apache.james.core.Username;
import org.apache.james.eventsourcing.eventstore.cassandra.CassandraEventStore;
import org.apache.james.eventsourcing.eventstore.cassandra.CassandraEventStoreModule;
import org.apache.james.eventsourcing.eventstore.cassandra.EventStoreDao;
import org.apache.james.eventsourcing.eventstore.cassandra.JsonEventSerializer;
import org.apache.james.junit.categories.Unstable;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.mail.eventsourcing.acl.ACLModule;
import org.apache.james.mailbox.cassandra.modules.CassandraAclModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxModule;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.exception.TooLongMailboxNameException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.mailbox.model.search.ExactName;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.mailbox.model.search.Wildcard;
import org.apache.james.mailbox.store.MailboxReactorUtils;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.fge.lambdas.Throwing;
import com.github.fge.lambdas.runnable.ThrowingRunnable;

import reactor.core.publisher.Mono;

class CassandraMailboxMapperTest {
    private static final UidValidity UID_VALIDITY = UidValidity.of(52);
    private static final Username USER = Username.of("user");
    private static final CassandraId MAILBOX_ID = CassandraId.timeBased();
    private static final MailboxPath MAILBOX_PATH = MailboxPath.forUser(USER, "name");
    private static final Mailbox MAILBOX = new Mailbox(MAILBOX_PATH, UID_VALIDITY, MAILBOX_ID);
    private static final String INBOX = "INBOX";
    private static final String INBOX_RENAMED = "INBOX_RENAMED";

    private static final CassandraId MAILBOX_ID_2 = CassandraId.timeBased();

    private static final Mailbox MAILBOX_BIS = new Mailbox(MAILBOX_PATH, UID_VALIDITY, MAILBOX_ID_2);

    private static final CassandraModule MODULES = CassandraModule.aggregateModules(
        CassandraAclModule.MODULE,
        CassandraEventStoreModule.MODULE(),
        CassandraMailboxModule.MODULE,
        CassandraSchemaVersionModule.MODULE);
    private static final int TRY_COUNT_BEFORE_FAILURE = 6;

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(MODULES);

    private CassandraMailboxDAO mailboxDAO;
    private CassandraMailboxPathV3DAO mailboxPathV3DAO;
    private CassandraMailboxMapper testee;
    private CassandraSchemaVersionDAO versionDAO;

    @BeforeEach
    void setUp() {
        CassandraCluster cassandra = cassandraCluster.getCassandraCluster();
        mailboxDAO = new CassandraMailboxDAO(cassandra.getConf(), cassandra.getTypesProvider());
        mailboxPathV3DAO = new CassandraMailboxPathV3DAO(cassandra.getConf());

        versionDAO = new CassandraSchemaVersionDAO(cassandra.getConf());
        versionDAO.truncateVersion()
            .then(versionDAO.updateVersion(new SchemaVersion(7)))
            .block();
        setUpTestee(CassandraConfiguration.DEFAULT_CONFIGURATION);
    }

    private void setUpTestee(CassandraConfiguration cassandraConfiguration) {
        CassandraCluster cassandra = cassandraCluster.getCassandraCluster();

        CassandraACLDAOV2 aclDAOv2 = new CassandraACLDAOV2(cassandra.getConf());
        JsonEventSerializer jsonEventSerializer = JsonEventSerializer
            .forModules(ACLModule.ACL_UPDATE)
            .withoutNestedType();
        CassandraUserMailboxRightsDAO usersRightDAO = new CassandraUserMailboxRightsDAO(cassandra.getConf());
        CassandraEventStore eventStore = new CassandraEventStore(new EventStoreDao(cassandra.getConf(), jsonEventSerializer));
        CassandraACLMapper aclMapper = new CassandraACLMapper(
            new CassandraACLMapper.StoreV2(usersRightDAO, aclDAOv2, eventStore),
            CassandraConfiguration.DEFAULT_CONFIGURATION);
        testee = new CassandraMailboxMapper(
            mailboxDAO,
            mailboxPathV3DAO,
            usersRightDAO,
            aclMapper,
            cassandraConfiguration);
    }

    @Nested
    class ConsistencyTest {

        private MailboxPath inboxPath;
        private MailboxPath inboxPathRenamed;
        private MailboxQuery.UserBound allMailboxesSearchQuery;
        private MailboxQuery.UserBound inboxSearchQuery;
        private MailboxQuery.UserBound inboxRenamedSearchQuery;

        @BeforeEach
        void setUp() {
            inboxPath = MailboxPath.forUser(USER, INBOX);
            inboxPathRenamed = MailboxPath.forUser(USER, INBOX_RENAMED);
            allMailboxesSearchQuery = MailboxQuery.builder()
                .userAndNamespaceFrom(inboxPath)
                .expression(Wildcard.INSTANCE)
                .build()
                .asUserBound();
            inboxSearchQuery = MailboxQuery.builder()
                .userAndNamespaceFrom(inboxPath)
                .expression(new ExactName(INBOX))
                .build()
                .asUserBound();
            inboxRenamedSearchQuery = MailboxQuery.builder()
                .userAndNamespaceFrom(inboxPathRenamed)
                .expression(new ExactName(INBOX_RENAMED))
                .build()
                .asUserBound();
        }

        @Nested
        class Retries {
            @Test
            void renameShouldRetryFailedDeleteMailboxPath(CassandraCluster cassandra) {
                Mailbox inbox = testee.create(inboxPath, UID_VALIDITY).block();
                MailboxId inboxId = inbox.getMailboxId();
                Mailbox inboxRenamed = createInboxRenamedMailbox(inboxId);

                cassandra.getConf()
                    .registerScenario(fail()
                        .times(1)
                        .whenQueryStartsWith("DELETE FROM mailboxpathv2"));

                testee.rename(inboxRenamed).block();

                SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                    softly(softly)
                        .assertThat(testee.findMailboxById(inboxId).block())
                        .isEqualTo(inboxRenamed);
                    softly(softly)
                        .assertThat(testee.findMailboxByPath(inboxPathRenamed).block())
                        .isEqualTo(inboxRenamed);
                    softly.assertThat(testee.findMailboxWithPathLike(allMailboxesSearchQuery)
                            .collectList().block())
                        .hasOnlyOneElementSatisfying(searchMailbox -> softly(softly)
                            .assertThat(searchMailbox)
                            .isEqualTo(inboxRenamed));
                }));
            }

            @Test
            void renameShouldRetryFailedMailboxSaving(CassandraCluster cassandra) {
                Mailbox inbox = testee.create(inboxPath, UID_VALIDITY).block();
                MailboxId inboxId = inbox.getMailboxId();
                Mailbox inboxRenamed = createInboxRenamedMailbox(inboxId);

                cassandra.getConf()
                    .registerScenario(fail()
                        .times(1)
                        .whenQueryStartsWith("INSERT INTO mailbox (id,name,uidvalidity,mailboxbase)"));

                testee.rename(inboxRenamed).block();

                SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                    softly(softly)
                        .assertThat(testee.findMailboxById(inboxId).block())
                        .isEqualTo(inboxRenamed);
                    softly(softly)
                        .assertThat(testee.findMailboxByPath(inboxPathRenamed).block())
                        .isEqualTo(inboxRenamed);
                    softly.assertThat(testee.findMailboxWithPathLike(allMailboxesSearchQuery)
                            .collectList().block())
                        .hasOnlyOneElementSatisfying(searchMailbox -> softly(softly)
                            .assertThat(searchMailbox)
                            .isEqualTo(inboxRenamed));
                }));
            }

            @Test
            void createShouldRetryFailedMailboxSaving(CassandraCluster cassandra) {
                cassandra.getConf()
                    .registerScenario(fail()
                        .times(1)
                        .whenQueryStartsWith("INSERT INTO mailbox (id,name,uidvalidity,mailboxbase)"));

                Mailbox inbox = testee.create(inboxPath, UID_VALIDITY).block();

                SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                    softly(softly)
                        .assertThat(testee.findMailboxById(inbox.getMailboxId()).block())
                        .isEqualTo(inbox);
                    softly(softly)
                        .assertThat(testee.findMailboxByPath(inboxPath).block())
                        .isEqualTo(inbox);
                    softly.assertThat(testee.findMailboxWithPathLike(allMailboxesSearchQuery)
                            .collectList().block())
                        .hasOnlyOneElementSatisfying(searchMailbox -> softly(softly)
                            .assertThat(searchMailbox)
                            .isEqualTo(inbox));
                }));
            }

            @Test
            void deleteShouldRetryFailedMailboxDeletion(CassandraCluster cassandra) {
                Mailbox inbox = testee.create(inboxPath, UID_VALIDITY).block();

                cassandra.getConf()
                    .registerScenario(fail()
                        .times(1)
                        .whenQueryStartsWith("DELETE FROM mailbox "));

                testee.delete(inbox).block();

                SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                    softly.assertThatThrownBy(() -> testee.findMailboxById(inbox.getMailboxId()).block())
                        .hasCauseInstanceOf(MailboxNotFoundException.class);
                    softly.assertThat(testee.findMailboxByPath(inboxPath).blockOptional())
                        .isEmpty();
                    softly.assertThat(testee.findMailboxWithPathLike(allMailboxesSearchQuery)
                            .collectList().block())
                        .isEmpty();
                }));
            }
        }

        @Nested
        class ReadRepairs {
            @BeforeEach
            void setVersion() {
                // Read repairs should not be performed with an outdated data representation
                versionDAO.truncateVersion()
                    .then(versionDAO.updateVersion(new SchemaVersion(8)))
                    .block();
            }

            @Test
            void findMailboxByIdShouldEventuallyFixInconsistencyWhenMailboxIsNotInPath() {
                mailboxDAO.save(MAILBOX)
                    .block();

                IntStream.range(0, 100).forEach(i ->
                    testee.findMailboxById(MAILBOX_ID)
                        .onErrorResume(e -> Mono.empty())
                        .block());

                SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                    softly(softly)
                        .assertThat(testee.findMailboxById(MAILBOX_ID).block())
                        .isEqualTo(MAILBOX);
                    softly(softly)
                        .assertThat(testee.findMailboxByPath(MAILBOX_PATH).block())
                        .isEqualTo(MAILBOX);
                }));
            }

            @Test
            void orphanMailboxIdEntriesCanNotBeReadRepaired() {
                mailboxDAO.save(MAILBOX)
                    .block();

                IntStream.range(0, 100).forEach(i ->
                    testee.findMailboxByPath(MAILBOX_PATH)
                        .onErrorResume(e -> Mono.empty())
                        .block());

                SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                    softly.assertThat(MailboxReactorUtils.blockOptional(testee.findMailboxByPath(MAILBOX_PATH)))
                        .isEmpty();
                    softly(softly)
                        .assertThat(testee.findMailboxById(MAILBOX_ID).block())
                        .isEqualTo(MAILBOX);
                }));
            }

            @Test
            void orphanPathEntriesCanNotBeRepairedByIdReads() {
                mailboxPathV3DAO.save(MAILBOX)
                    .block();

                IntStream.range(0, 100).forEach(i ->
                    testee.findMailboxById(MAILBOX_ID)
                        .onErrorResume(e -> Mono.empty())
                        .block());

                SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                    softly.assertThatThrownBy(() -> MailboxReactorUtils.blockOptional(testee.findMailboxById(MAILBOX_ID)))
                        .isInstanceOf(MailboxNotFoundException.class);
                    softly(softly)
                        .assertThat(testee.findMailboxByPath(MAILBOX_PATH).block())
                        .isEqualTo(MAILBOX);
                }));
            }

            @Test
            void findMailboxByPathShouldFixInconsistencyWhenMailboxIsNotReferencedById() {
                mailboxPathV3DAO.save(MAILBOX)
                    .block();

                IntStream.range(0, 100).forEach(i ->
                    testee.findMailboxByPath(MAILBOX_PATH)
                        .onErrorResume(e -> Mono.empty())
                        .block());

                SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                    softly.assertThatThrownBy(() -> MailboxReactorUtils.blockOptional(testee.findMailboxById(MAILBOX_ID)))
                        .isInstanceOf(MailboxNotFoundException.class);
                    softly.assertThat(MailboxReactorUtils.blockOptional(testee.findMailboxByPath(MAILBOX_PATH)))
                        .isEmpty();
                }));
            }
        }

        @Disabled("In order to be more performant mailboxPath V3 table includes the UID_VALIDITY." +
            "Reading paths no longer requires reading the mailbox by id but this of course has a " +
            "consistency cost.")
        @Test
        void createShouldBeConsistentWhenFailToPersistMailbox(CassandraCluster cassandra) {
            cassandra.getConf()
                .registerScenario(fail()
                    .times(10)
                    .whenQueryStartsWith("INSERT INTO mailbox (id,name,uidvalidity,mailboxbase)"));

            doQuietly(() -> testee.create(inboxPath, UID_VALIDITY).block());

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(testee.findMailboxByPath(inboxPath).blockOptional())
                    .isEmpty();
                softly.assertThat(testee.findMailboxWithPathLike(inboxSearchQuery)
                        .collectList().block())
                    .isEmpty();
                softly.assertThat(testee.findMailboxWithPathLike(allMailboxesSearchQuery)
                        .collectList().block())
                    .isEmpty();
            });
        }

        @Test
        void renameThenFailToRetrieveMailboxShouldBeConsistentWhenFindByInbox(CassandraCluster cassandra) {
            Mailbox inbox = testee.create(inboxPath, UID_VALIDITY).block();
            CassandraId inboxId = (CassandraId) inbox.getMailboxId();
            Mailbox inboxRenamed = createInboxRenamedMailbox(inboxId);

            cassandra.getConf()
                .registerScenario(fail()
                    .times(TRY_COUNT_BEFORE_FAILURE)
                    .whenQueryStartsWith("SELECT id,mailboxbase,uidvalidity,name FROM mailbox"));

            doQuietly(() -> testee.rename(inboxRenamed));

            cassandra.getConf().registerScenario(Scenario.NOTHING);

            SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                softly(softly)
                    .assertThat(testee.findMailboxById(inboxId).block())
                    .isEqualTo(inbox);
                softly(softly)
                    .assertThat(testee.findMailboxByPath(inboxPath).block())
                    .isEqualTo(inbox);
                softly.assertThat(testee.findMailboxWithPathLike(inboxSearchQuery)
                        .collectList().block())
                    .hasOnlyOneElementSatisfying(searchMailbox -> softly(softly)
                        .assertThat(searchMailbox)
                        .isEqualTo(inbox));
            }));
        }

        @Disabled("JAMES-3056 returning two mailboxes with same name and id")
        @Test
        void renameThenFailToRetrieveMailboxShouldBeConsistentWhenFindAll(CassandraCluster cassandra) {
            Mailbox inbox = testee.create(inboxPath, UID_VALIDITY).block();
            CassandraId inboxId = (CassandraId) inbox.getMailboxId();
            Mailbox inboxRenamed = createInboxRenamedMailbox(inboxId);

            cassandra.getConf()
                .registerScenario(fail()
                    .times(TRY_COUNT_BEFORE_FAILURE)
                    .whenQueryStartsWith("SELECT id,mailboxbase,uidvalidity,name FROM mailbox"));

            doQuietly(() -> testee.rename(inboxRenamed).block());

            SoftAssertions.assertSoftly(Throwing.consumer(softly ->
                softly.assertThat(testee.findMailboxWithPathLike(allMailboxesSearchQuery)
                        .collectList().block())
                    .hasOnlyOneElementSatisfying(searchMailbox -> softly(softly)
                        .assertThat(searchMailbox)
                        .isEqualTo(inbox))
            ));
        }

        @Disabled("JAMES-3056 find by renamed name returns unexpected results")
        @Test
        void renameThenFailToRetrieveMailboxShouldBeConsistentWhenFindByRenamedInbox(CassandraCluster cassandra) {
            Mailbox inbox = testee.create(inboxPath, UID_VALIDITY).block();
            CassandraId inboxId = (CassandraId) inbox.getMailboxId();
            Mailbox inboxRenamed = createInboxRenamedMailbox(inboxId);

            cassandra.getConf()
                .registerScenario(fail()
                    .times(TRY_COUNT_BEFORE_FAILURE)
                    .whenQueryStartsWith("SELECT id,mailboxbase,uidvalidity,name FROM mailbox WHERE id=:id"));

            doQuietly(() -> testee.rename(inboxRenamed).block());

            SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                softly.assertThatThrownBy(() -> testee.findMailboxByPath(inboxPathRenamed).block())
                    .isInstanceOf(MailboxNotFoundException.class);
                softly.assertThat(testee.findMailboxWithPathLike(inboxRenamedSearchQuery)
                        .collectList().block())
                    .isEmpty();
            }));
        }

        @Test
        void renameThenFailToDeleteMailboxPathShouldBeConsistentWhenFindByInbox(CassandraCluster cassandra) {
            Mailbox inbox = testee.create(inboxPath, UID_VALIDITY).block();
            CassandraId inboxId = (CassandraId) inbox.getMailboxId();
            Mailbox inboxRenamed = createInboxRenamedMailbox(inboxId);

            cassandra.getConf()
                .registerScenario(fail()
                    .times(TRY_COUNT_BEFORE_FAILURE)
                    .whenQueryStartsWith("DELETE FROM mailboxpathv3 WHERE namespace=:namespace AND user=:user AND mailboxname=:mailboxname IF EXISTS"));

            doQuietly(() -> testee.rename(inboxRenamed).block());

            SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                softly(softly)
                    .assertThat(testee.findMailboxById(inboxId).block())
                    .isEqualTo(inbox);
                softly(softly)
                    .assertThat(testee.findMailboxByPath(inboxPath).block())
                    .isEqualTo(inbox);
                softly.assertThat(testee.findMailboxWithPathLike(inboxSearchQuery)
                        .collectList().block())
                    .hasOnlyOneElementSatisfying(searchMailbox -> softly(softly)
                        .assertThat(searchMailbox)
                        .isEqualTo(inbox));
            }));
        }

        @Disabled("JAMES-3056 returning two mailboxes with same name and id")
        @Test
        void renameThenFailToDeleteMailboxPathShouldBeConsistentWhenFindAll(CassandraCluster cassandra) {
            Mailbox inbox = testee.create(inboxPath, UID_VALIDITY).block();
            CassandraId inboxId = (CassandraId) inbox.getMailboxId();
            Mailbox inboxRenamed = createInboxRenamedMailbox(inboxId);

            cassandra.getConf()
                .registerScenario(fail()
                    .times(TRY_COUNT_BEFORE_FAILURE)
                    .whenQueryStartsWith("DELETE FROM mailboxpathv3 WHERE namespace=:namespace AND user=:user AND mailboxname=:mailboxname IF EXISTS"));

            doQuietly(() -> testee.rename(inboxRenamed).block());

            SoftAssertions.assertSoftly(Throwing.consumer(softly ->
                softly.assertThat(testee.findMailboxWithPathLike(allMailboxesSearchQuery)
                        .collectList().block())
                    .hasOnlyOneElementSatisfying(searchMailbox -> softly(softly)
                        .assertThat(searchMailbox)
                        .isEqualTo(inbox))));
        }

        @Disabled("JAMES-3056 find by renamed name returns unexpected results")
        @Test
        void renameThenFailToDeleteMailboxPathShouldBeConsistentWhenFindByRenamedInbox(CassandraCluster cassandra) {
            Mailbox inbox = testee.create(inboxPath, UID_VALIDITY).block();
            CassandraId inboxId = (CassandraId) inbox.getMailboxId();
            Mailbox inboxRenamed = createInboxRenamedMailbox(inboxId);

            cassandra.getConf()
                .registerScenario(fail()
                    .times(TRY_COUNT_BEFORE_FAILURE)
                    .whenQueryStartsWith("DELETE FROM mailboxpathv3 WHERE namespace=:namespace AND user=:user AND mailboxname=:mailboxname IF EXISTS"));

            doQuietly(() -> testee.rename(inboxRenamed).block());

            SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                softly.assertThatThrownBy(() -> testee.findMailboxByPath(inboxPathRenamed).block())
                    .isInstanceOf(MailboxNotFoundException.class);
                softly.assertThat(testee.findMailboxWithPathLike(inboxRenamedSearchQuery)
                        .collectList().block())
                    .isEmpty();
            }));
        }

        @Disabled("JAMES-3056 find by mailbox name returns unexpected results")
        @Test
        void deleteShouldBeConsistentWhenFailToDeleteMailbox(CassandraCluster cassandra) {
            Mailbox inbox = testee.create(inboxPath, UID_VALIDITY).block();
            CassandraId inboxId = (CassandraId) inbox.getMailboxId();

            cassandra.getConf()
                .registerScenario(fail()
                    .times(TRY_COUNT_BEFORE_FAILURE)
                    .whenQueryStartsWith("DELETE FROM mailbox WHERE id=:id"));

            doQuietly(() -> testee.delete(inbox).block());

            SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                softly.assertThatCode(() -> testee.findMailboxById(inboxId).block())
                    .doesNotThrowAnyException();
                softly.assertThatCode(() -> testee.findMailboxByPath(inboxPath).block())
                    .doesNotThrowAnyException();
                softly.assertThat(testee.findMailboxWithPathLike(inboxSearchQuery)
                        .collectList().block())
                    .hasOnlyOneElementSatisfying(searchMailbox -> softly(softly)
                        .assertThat(searchMailbox)
                        .isEqualTo(inbox));
                softly.assertThat(testee.findMailboxWithPathLike(allMailboxesSearchQuery)
                        .collectList().block())
                    .hasOnlyOneElementSatisfying(searchMailbox -> softly(softly)
                        .assertThat(searchMailbox)
                        .isEqualTo(inbox));
            }));
        }

        @Disabled("JAMES-3056 org.apache.james.mailbox.exception.MailboxNotFoundException: 'mailboxId' can not be found")
        @Test
        void createAfterPreviousFailedCreateShouldCreateAMailbox(CassandraCluster cassandra) {
            cassandra.getConf()
                .registerScenario(fail()
                    .times(TRY_COUNT_BEFORE_FAILURE)
                    .whenQueryStartsWith("INSERT INTO mailbox (id,name,uidvalidity,mailboxbase) VALUES (:id,:name,:uidvalidity,:mailboxbase)"));

            doQuietly(() -> testee.create(inboxPath, UID_VALIDITY).block());

            Mailbox inbox = testee.create(inboxPath, UID_VALIDITY).block();

            SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                softly(softly)
                    .assertThat(testee.findMailboxByPath(inboxPath).block())
                    .isEqualTo(inbox);
                softly.assertThat(testee.findMailboxWithPathLike(inboxSearchQuery)
                        .collectList().block())
                    .hasOnlyOneElementSatisfying(searchMailbox -> softly(softly)
                        .assertThat(searchMailbox)
                        .isEqualTo(inbox));
                softly.assertThat(testee.findMailboxWithPathLike(allMailboxesSearchQuery)
                        .collectList().block())
                    .hasOnlyOneElementSatisfying(searchMailbox -> softly(softly)
                        .assertThat(searchMailbox)
                        .isEqualTo(inbox));
            }));
        }

        @Test
        /*
        https://builds.apache.org/blue/organizations/jenkins/james%2FApacheJames/detail/PR-268/38/tests
        Expected size:<1> but was:<2> in:
        <[Mailbox{id=44ec7c50-405c-11eb-bd9e-f9735674a69e, namespace=#private, user=Username{localPart=user, domainPart=Optional.empty}, name=INBOX},
            Mailbox{id=4282f660-405c-11eb-bd9e-f9735674a69e, namespace=#private, user=Username{localPart=user, domainPart=Optional.empty}, name=name}]>
        at CassandraMailboxMapperTest$ConsistencyTest.lambda$createAfterPreviousDeleteOnFailedCreateShouldCreateAMailbox$34(CassandraMailboxMapperTest$ConsistencyTest.java:628)
         */
        @Tag(Unstable.TAG)
        void createAfterPreviousDeleteOnFailedCreateShouldCreateAMailbox(CassandraCluster cassandra) {
            cassandra.getConf()
                .registerScenario(fail()
                    .times(TRY_COUNT_BEFORE_FAILURE)
                    .whenQueryStartsWith("INSERT INTO mailbox (id,name,uidvalidity,mailboxbase) VALUES (:id,:name,:uidvalidity,:mailboxbase)"));

            doQuietly(() -> testee.create(inboxPath, UID_VALIDITY).block());
            doQuietly(() -> testee.delete(new Mailbox(inboxPath, UID_VALIDITY, CassandraId.timeBased())).block());

            Mailbox inbox = testee.create(inboxPath, UID_VALIDITY).block();

            SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                softly(softly)
                    .assertThat(testee.findMailboxByPath(inboxPath).block())
                    .isEqualTo(inbox);
                softly.assertThat(testee.findMailboxWithPathLike(inboxSearchQuery)
                        .collectList().block())
                    .hasOnlyOneElementSatisfying(searchMailbox -> softly(softly)
                        .assertThat(searchMailbox)
                        .isEqualTo(inbox));
                softly.assertThat(testee.findMailboxWithPathLike(allMailboxesSearchQuery)
                        .collectList().block())
                    .hasOnlyOneElementSatisfying(searchMailbox -> softly(softly)
                        .assertThat(searchMailbox)
                        .isEqualTo(inbox));
            }));
        }

        @Test
        void deleteAfterAFailedDeleteShouldDeleteTheMailbox(CassandraCluster cassandra) {
            Mailbox inbox = testee.create(inboxPath, UID_VALIDITY).block();
            CassandraId inboxId = (CassandraId) inbox.getMailboxId();

            cassandra.getConf()
                .registerScenario(fail()
                    .times(TRY_COUNT_BEFORE_FAILURE)
                    .whenQueryStartsWith("DELETE FROM mailbox WHERE id=:id"));

            doQuietly(() -> testee.delete(inbox).block());

            doQuietly(() -> testee.delete(inbox).block());

            SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                softly.assertThatThrownBy(() -> testee.findMailboxById(inboxId).block())
                    .hasCauseInstanceOf(MailboxNotFoundException.class);
                softly.assertThat(testee.findMailboxByPath(inboxPath).blockOptional())
                    .isEmpty();
                softly.assertThat(testee.findMailboxWithPathLike(inboxSearchQuery)
                        .collectList().block())
                    .isEmpty();
                softly.assertThat(testee.findMailboxWithPathLike(allMailboxesSearchQuery)
                        .collectList().block())
                    .isEmpty();
            }));
        }

        @Disabled("JAMES-3056 mailbox name is not updated to INBOX_RENAMED).isEqualTo(" +
            "findMailboxWithPathLike() returns a list with two same mailboxes")
        @Test
        void renameAfterRenameFailOnRetrieveMailboxShouldRenameTheMailbox(CassandraCluster cassandra) {
            Mailbox inbox = testee.create(inboxPath, UID_VALIDITY).block();
            CassandraId inboxId = (CassandraId) inbox.getMailboxId();
            Mailbox inboxRenamed = createInboxRenamedMailbox(inboxId);

            cassandra.getConf()
                .registerScenario(fail()
                    .times(TRY_COUNT_BEFORE_FAILURE)
                    .whenQueryStartsWith("SELECT id,mailboxbase,uidvalidity,name FROM mailbox WHERE id=:id"));

            doQuietly(() -> testee.rename(inboxRenamed).block());

            doQuietly(() -> testee.rename(inboxRenamed).block());

            SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                softly(softly)
                    .assertThat(testee.findMailboxById(inboxId).block())
                    .isEqualTo(inboxRenamed);
                softly(softly)
                    .assertThat(testee.findMailboxByPath(inboxPathRenamed).block())
                    .isEqualTo(inboxRenamed);
                softly.assertThat(testee.findMailboxWithPathLike(inboxSearchQuery)
                        .collectList().block())
                    .isEmpty();
                softly.assertThat(testee.findMailboxWithPathLike(inboxRenamedSearchQuery)
                        .collectList().block())
                    .hasOnlyOneElementSatisfying(searchMailbox -> softly(softly)
                        .assertThat(searchMailbox)
                        .isEqualTo(inboxRenamed));
                softly.assertThat(testee.findMailboxWithPathLike(allMailboxesSearchQuery)
                        .collectList().block())
                    .hasOnlyOneElementSatisfying(searchMailbox -> softly(softly)
                        .assertThat(searchMailbox)
                        .isEqualTo(inboxRenamed));
            }));
        }

        @Disabled("JAMES-3056 mailbox name is not updated to INBOX_RENAMED")
        @Test
        void renameAfterRenameFailOnDeletePathShouldRenameTheMailbox(CassandraCluster cassandra) {
            Mailbox inbox = testee.create(inboxPath, UID_VALIDITY).block();
            CassandraId inboxId = (CassandraId) inbox.getMailboxId();
            Mailbox inboxRenamed = createInboxRenamedMailbox(inboxId);

            cassandra.getConf()
                .registerScenario(fail()
                    .times(TRY_COUNT_BEFORE_FAILURE)
                    .whenQueryStartsWith("DELETE FROM mailboxpathv3 WHERE namespace=:namespace AND user=:user AND mailboxname=:mailboxname IF EXISTS"));

            doQuietly(() -> testee.rename(inboxRenamed).block());

            doQuietly(() -> testee.rename(inboxRenamed).block());

            SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                softly(softly)
                    .assertThat(testee.findMailboxById(inboxId).block())
                    .isEqualTo(inboxRenamed);
                softly(softly)
                    .assertThat(testee.findMailboxByPath(inboxPathRenamed).block())
                    .isEqualTo(inboxRenamed);
                softly.assertThat(testee.findMailboxWithPathLike(inboxSearchQuery)
                        .collectList().block())
                    .isEmpty();
                softly.assertThat(testee.findMailboxWithPathLike(inboxRenamedSearchQuery)
                        .collectList().block())
                    .hasOnlyOneElementSatisfying(searchMailbox ->
                        softly(softly)
                            .assertThat(searchMailbox)
                            .isEqualTo(inboxRenamed));
                softly.assertThat(testee.findMailboxWithPathLike(allMailboxesSearchQuery)
                        .collectList().block())
                    .hasOnlyOneElementSatisfying(searchMailbox -> softly(softly)
                        .assertThat(searchMailbox)
                        .isEqualTo(inboxRenamed));
            }));
        }

        private void doQuietly(ThrowingRunnable runnable) {
            try {
                runnable.run();
            } catch (Throwable th) {
                // ignore
            }
        }

        private Mailbox createInboxRenamedMailbox(MailboxId mailboxId) {
            return new Mailbox(inboxPathRenamed, UID_VALIDITY, mailboxId);
        }
    }

    @Disabled("JAMES-2514 Cassandra 3 supports long mailbox names. Hence we can not rely on this for failing")
    @Test
    void renameShouldNotRemoveOldMailboxPathWhenCreatingTheNewMailboxPathFails() {
        testee.create(MAILBOX_PATH, UID_VALIDITY).block();
        Mailbox mailbox = testee.findMailboxByPath(MAILBOX_PATH).block();

        Mailbox newMailbox = new Mailbox(tooLongMailboxPath(mailbox.generateAssociatedPath()), UID_VALIDITY, mailbox.getMailboxId());
        assertThatThrownBy(() -> testee.rename(newMailbox).block())
            .isInstanceOf(TooLongMailboxNameException.class);

        assertThat(mailboxPathV3DAO.retrieve(MAILBOX_PATH).blockOptional())
            .isPresent();
    }

    private MailboxPath tooLongMailboxPath(MailboxPath fromMailboxPath) {
        return new MailboxPath(fromMailboxPath, StringUtils.repeat("b", 65537));
    }
}
