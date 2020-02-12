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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.apache.james.core.Username;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.modules.CassandraAclModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxModule;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.exception.TooLongMailboxNameException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MailboxSoftlyAssert;
import org.apache.james.mailbox.model.search.ExactName;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.mailbox.model.search.Wildcard;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.fge.lambdas.Throwing;
import com.github.fge.lambdas.runnable.ThrowingRunnable;

import reactor.core.publisher.Mono;

class CassandraMailboxMapperTest {
    private static final int UID_VALIDITY = 52;
    private static final Username USER = Username.of("user");
    private static final CassandraId MAILBOX_ID = CassandraId.timeBased();
    private static final MailboxPath MAILBOX_PATH = MailboxPath.forUser(USER, "name");
    private static final Mailbox MAILBOX = new Mailbox(MAILBOX_PATH, UID_VALIDITY, MAILBOX_ID);
    private static final String INBOX = "INBOX";
    private static final String INBOX_RENAMED = "INBOX_RENAMED";

    private static final CassandraId MAILBOX_ID_2 = CassandraId.timeBased();

    private static final Mailbox MAILBOX_BIS = new Mailbox(MAILBOX_PATH, UID_VALIDITY, MAILBOX_ID_2);

    private static final CassandraModule MODULES = CassandraModule.aggregateModules(
        CassandraMailboxModule.MODULE,
        CassandraSchemaVersionModule.MODULE,
        CassandraAclModule.MODULE);

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(MODULES);

    private CassandraMailboxDAO mailboxDAO;
    private CassandraMailboxPathDAOImpl mailboxPathDAO;
    private CassandraMailboxPathV2DAO mailboxPathV2DAO;
    private CassandraMailboxMapper testee;
    private CassandraACLMapper aclMapper;

    @BeforeEach
    void setUp() {
        CassandraCluster cassandra = cassandraCluster.getCassandraCluster();
        mailboxDAO = spy(new CassandraMailboxDAO(cassandra.getConf(), cassandra.getTypesProvider()));
        mailboxPathDAO = spy(new CassandraMailboxPathDAOImpl(cassandra.getConf(), cassandra.getTypesProvider()));
        mailboxPathV2DAO = spy(new CassandraMailboxPathV2DAO(cassandra.getConf(), CassandraUtils.WITH_DEFAULT_CONFIGURATION));
        CassandraUserMailboxRightsDAO userMailboxRightsDAO = new CassandraUserMailboxRightsDAO(cassandra.getConf(), CassandraUtils.WITH_DEFAULT_CONFIGURATION);
        aclMapper = spy(new CassandraACLMapper(
            cassandra.getConf(),
            new CassandraUserMailboxRightsDAO(cassandra.getConf(), CassandraUtils.WITH_DEFAULT_CONFIGURATION),
            CassandraConfiguration.DEFAULT_CONFIGURATION));
        testee = new CassandraMailboxMapper(
            mailboxDAO,
            mailboxPathDAO,
            mailboxPathV2DAO,
            userMailboxRightsDAO,
            aclMapper);
    }

    @Nested
    class ConsistencyTest {

        private CassandraId inboxId;
        private MailboxPath inboxPath;
        private Mailbox inbox;
        private MailboxPath inboxPathRenamed;
        private Mailbox inboxRenamed;
        private MailboxQuery.UserBound allMailboxesSearchQuery;
        private MailboxQuery.UserBound inboxSearchQuery;
        private MailboxQuery.UserBound inboxRenamedSearchQuery;

        @BeforeEach
        void setUp() {
            inboxId = CassandraId.timeBased();
            inboxPath = MailboxPath.forUser(USER, INBOX);
            inbox = new Mailbox(inboxPath, UID_VALIDITY, inboxId);

            inboxPathRenamed = MailboxPath.forUser(USER, INBOX_RENAMED);
            inboxRenamed = new Mailbox(inboxPathRenamed, UID_VALIDITY, inboxId);
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

        @Test
        void saveOnCreateShouldBeConsistentWhenFailToPersistMailbox() {
            doReturn(Mono.error(new RuntimeException("mock exception")))
                .when(mailboxDAO)
                .save(inbox);

            doQuietly(() -> testee.save(inbox));

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThatThrownBy(() -> testee.findMailboxById(inboxId))
                    .isInstanceOf(MailboxNotFoundException.class);
                softly.assertThatThrownBy(() -> testee.findMailboxByPath(inboxPath))
                    .isInstanceOf(MailboxNotFoundException.class);
                softly.assertThat(testee.findMailboxWithPathLike(inboxSearchQuery))
                    .isEmpty();
                softly.assertThat(testee.findMailboxWithPathLike(allMailboxesSearchQuery))
                    .isEmpty();
            });
        }

        @Test
        void saveOnRenameThenFailToGetACLShouldBeConsistentWhenFindByInbox() throws Exception {
            testee.save(inbox);

            when(aclMapper.getACL(inboxId))
                .thenReturn(Mono.error(new RuntimeException("mock exception")))
                .thenCallRealMethod();

            doQuietly(() -> testee.save(inboxRenamed));

            SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                MailboxSoftlyAssert.softly(softly)
                    .assertThat(testee.findMailboxById(inboxId))
                    .isEqualTo(inbox);
                MailboxSoftlyAssert.softly(softly)
                    .assertThat(testee.findMailboxByPath(inboxPath))
                    .isEqualTo(inbox);
                softly.assertThat(testee.findMailboxWithPathLike(inboxSearchQuery))
                    .hasOnlyOneElementSatisfying(searchMailbox -> MailboxSoftlyAssert.softly(softly)
                        .assertThat(searchMailbox)
                        .isEqualTo(inbox));
            }));
        }

        @Disabled("JAMES-3056 returning two mailboxes with same name and id")
        @Test
        void saveOnRenameThenFailToGetACLShouldBeConsistentWhenFindAll() throws Exception {
            testee.save(inbox);

            when(aclMapper.getACL(inboxId))
                .thenReturn(Mono.error(new RuntimeException("mock exception")))
                .thenCallRealMethod();

            doQuietly(() -> testee.save(inboxRenamed));

            SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                softly.assertThat(testee.findMailboxWithPathLike(allMailboxesSearchQuery))
                    .hasOnlyOneElementSatisfying(searchMailbox -> MailboxSoftlyAssert.softly(softly)
                        .assertThat(searchMailbox)
                        .isEqualTo(inbox));
            }));
        }

        @Disabled("JAMES-3056 find by renamed name returns unexpected results")
        @Test
        void saveOnRenameThenFailToGetACLShouldBeConsistentWhenFindByRenamedInbox() throws Exception {
            testee.save(inbox);

            when(aclMapper.getACL(inboxId))
                .thenReturn(Mono.error(new RuntimeException("mock exception")))
                .thenCallRealMethod();

            doQuietly(() -> testee.save(inboxRenamed));

            SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                softly.assertThatThrownBy(() -> testee.findMailboxByPath(inboxPathRenamed))
                    .isInstanceOf(MailboxNotFoundException.class);
                softly.assertThat(testee.findMailboxWithPathLike(inboxRenamedSearchQuery))
                    .isEmpty();
            }));
        }

        @Test
        void saveOnRenameThenFailToRetrieveMailboxShouldBeConsistentWhenFindByInbox() throws Exception {
            testee.save(inbox);

            when(mailboxDAO.retrieveMailbox(inboxId))
                .thenReturn(Mono.error(new RuntimeException("mock exception")))
                .thenCallRealMethod();

            doQuietly(() -> testee.save(inboxRenamed));

            SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                MailboxSoftlyAssert.softly(softly)
                    .assertThat(testee.findMailboxById(inboxId))
                    .isEqualTo(inbox);
                MailboxSoftlyAssert.softly(softly)
                    .assertThat(testee.findMailboxByPath(inboxPath))
                    .isEqualTo(inbox);
                softly.assertThat(testee.findMailboxWithPathLike(inboxSearchQuery))
                    .hasOnlyOneElementSatisfying(searchMailbox -> MailboxSoftlyAssert.softly(softly)
                        .assertThat(searchMailbox)
                        .isEqualTo(inbox));
            }));
        }

        @Disabled("JAMES-3056 returning two mailboxes with same name and id")
        @Test
        void saveOnRenameThenFailToRetrieveMailboxShouldBeConsistentWhenFindAll() throws Exception {
            testee.save(inbox);

            when(mailboxDAO.retrieveMailbox(inboxId))
                .thenReturn(Mono.error(new RuntimeException("mock exception")))
                .thenCallRealMethod();

            doQuietly(() -> testee.save(inboxRenamed));

            SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                softly.assertThat(testee.findMailboxWithPathLike(allMailboxesSearchQuery))
                    .hasOnlyOneElementSatisfying(searchMailbox -> MailboxSoftlyAssert.softly(softly)
                        .assertThat(searchMailbox)
                        .isEqualTo(inbox));
            }));
        }

        @Disabled("JAMES-3056 find by renamed name returns unexpected results")
        @Test
        void saveOnRenameThenFailToRetrieveMailboxShouldBeConsistentWhenFindByRenamedInbox() throws Exception {
            testee.save(inbox);

            when(mailboxDAO.retrieveMailbox(inboxId))
                .thenReturn(Mono.error(new RuntimeException("mock exception")))
                .thenCallRealMethod();

            doQuietly(() -> testee.save(inboxRenamed));

            SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                softly.assertThatThrownBy(() -> testee.findMailboxByPath(inboxPathRenamed))
                    .isInstanceOf(MailboxNotFoundException.class);
                softly.assertThat(testee.findMailboxWithPathLike(inboxRenamedSearchQuery))
                    .isEmpty();
            }));
        }

        @Test
        void saveOnRenameThenFailToDeleteMailboxPathShouldBeConsistentWhenFindByInbox() throws Exception {
            testee.save(inbox);

            when(mailboxPathV2DAO.delete(inboxPath))
                .thenReturn(Mono.error(new RuntimeException("mock exception")))
                .thenCallRealMethod();

            doQuietly(() -> testee.save(inboxRenamed));

            SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                MailboxSoftlyAssert.softly(softly)
                    .assertThat(testee.findMailboxById(inboxId))
                    .isEqualTo(inbox);
                MailboxSoftlyAssert.softly(softly)
                    .assertThat(testee.findMailboxByPath(inboxPath))
                    .isEqualTo(inbox);
                softly.assertThat(testee.findMailboxWithPathLike(inboxSearchQuery))
                    .hasOnlyOneElementSatisfying(searchMailbox -> MailboxSoftlyAssert.softly(softly)
                        .assertThat(searchMailbox)
                        .isEqualTo(inbox));
            }));
        }

        @Disabled("JAMES-3056 returning two mailboxes with same name and id")
        @Test
        void saveOnRenameThenFailToDeleteMailboxPathShouldBeConsistentWhenFindAll() throws Exception {
            testee.save(inbox);

            when(mailboxPathV2DAO.delete(inboxPath))
                .thenReturn(Mono.error(new RuntimeException("mock exception")))
                .thenCallRealMethod();

            doQuietly(() -> testee.save(inboxRenamed));

            SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                softly.assertThat(testee.findMailboxWithPathLike(allMailboxesSearchQuery))
                    .hasOnlyOneElementSatisfying(searchMailbox -> MailboxSoftlyAssert.softly(softly)
                        .assertThat(searchMailbox)
                        .isEqualTo(inbox));
            }));
        }

        @Disabled("JAMES-3056 find by renamed name returns unexpected results")
        @Test
        void saveOnRenameThenFailToDeleteMailboxPathShouldBeConsistentWhenFindByRenamedInbox() throws Exception {
            testee.save(inbox);

            when(mailboxPathV2DAO.delete(inboxPath))
                .thenReturn(Mono.error(new RuntimeException("mock exception")))
                .thenCallRealMethod();

            doQuietly(() -> testee.save(inboxRenamed));

            SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                softly.assertThatThrownBy(() -> testee.findMailboxByPath(inboxPathRenamed))
                    .isInstanceOf(MailboxNotFoundException.class);
                softly.assertThat(testee.findMailboxWithPathLike(inboxRenamedSearchQuery))
                    .isEmpty();
            }));
        }

        @Disabled("JAMES-3056 find by mailbox name returns unexpected results")
        @Test
        void deleteShouldBeConsistentWhenFailToDeleteMailbox() throws Exception {
            testee.save(inbox);

            doReturn(Mono.error(new RuntimeException("mock exception")))
                .when(mailboxDAO)
                .delete(inboxId);

            doQuietly(() -> testee.delete(inbox));

            SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                softly.assertThatCode(() -> testee.findMailboxById(inboxId))
                    .doesNotThrowAnyException();
                softly.assertThatCode(() -> testee.findMailboxByPath(inboxPath))
                    .doesNotThrowAnyException();
                softly.assertThat(testee.findMailboxWithPathLike(inboxSearchQuery))
                    .hasOnlyOneElementSatisfying(searchMailbox -> MailboxSoftlyAssert.softly(softly)
                        .assertThat(searchMailbox)
                        .isEqualTo(inbox));
                softly.assertThat(testee.findMailboxWithPathLike(allMailboxesSearchQuery))
                    .hasOnlyOneElementSatisfying(searchMailbox -> MailboxSoftlyAssert.softly(softly)
                        .assertThat(searchMailbox)
                        .isEqualTo(inbox));
            }));
        }

        @Disabled("JAMES-3056 both mailboxes of the same user have 'INBOX' name")
        @Test
        void missedMigrationShouldNotLeadToGhostMailbox() throws Exception {
            testee.save(inbox);
            // simulate mailbox old data has not been migrated to v2
            mailboxPathDAO.save(inboxPath, inboxId).block();
            mailboxPathV2DAO.delete(inboxPath).block();

            // on current v2 generation, save a new mailbox with the exactly name
            // => two mailboxes with same name but different ids
            CassandraId newId = CassandraId.timeBased();
            Mailbox mailboxHasSameNameWithInbox = new Mailbox(inboxPath, UID_VALIDITY, newId);
            testee.save(mailboxHasSameNameWithInbox);

            assertThat(testee.findMailboxById(newId).getName())
                .isNotEqualTo(testee.findMailboxById(inboxId).getName());
        }

        @Disabled("JAMES-3056 org.apache.james.mailbox.exception.MailboxNotFoundException: 'mailboxId' can not be found")
        @Test
        void saveAfterPreviousFailedSaveShouldCreateAMailbox() {
            when(mailboxDAO.save(inbox))
                .thenReturn(Mono.error(new RuntimeException("mock exception")))
                .thenCallRealMethod();

            doQuietly(() -> testee.save(inbox));
            doQuietly(() -> testee.save(inbox));

            SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                MailboxSoftlyAssert.softly(softly)
                    .assertThat(testee.findMailboxById(inboxId))
                    .isEqualTo(inbox);
                MailboxSoftlyAssert.softly(softly)
                    .assertThat(testee.findMailboxByPath(inboxPath))
                    .isEqualTo(inbox);
                softly.assertThat(testee.findMailboxWithPathLike(inboxSearchQuery))
                    .hasOnlyOneElementSatisfying(searchMailbox -> MailboxSoftlyAssert.softly(softly)
                        .assertThat(searchMailbox)
                        .isEqualTo(inbox));
                softly.assertThat(testee.findMailboxWithPathLike(allMailboxesSearchQuery))
                    .hasOnlyOneElementSatisfying(searchMailbox -> MailboxSoftlyAssert.softly(softly)
                        .assertThat(searchMailbox)
                        .isEqualTo(inbox));
            }));
        }

        @Test
        void saveAfterPreviousDeleteOnFailedSaveShouldCreateAMailbox() {
            when(mailboxDAO.save(inbox))
                .thenReturn(Mono.error(new RuntimeException("mock exception")))
                .thenCallRealMethod();

            doQuietly(() -> testee.save(inbox));
            doQuietly(() -> testee.delete(inbox));
            doQuietly(() -> testee.save(inbox));

            SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                MailboxSoftlyAssert.softly(softly)
                    .assertThat(testee.findMailboxById(inboxId))
                    .isEqualTo(inbox);
                MailboxSoftlyAssert.softly(softly)
                    .assertThat(testee.findMailboxByPath(inboxPath))
                    .isEqualTo(inbox);
                softly.assertThat(testee.findMailboxWithPathLike(inboxSearchQuery))
                    .hasOnlyOneElementSatisfying(searchMailbox -> MailboxSoftlyAssert.softly(softly)
                        .assertThat(searchMailbox)
                        .isEqualTo(inbox));
                softly.assertThat(testee.findMailboxWithPathLike(allMailboxesSearchQuery))
                    .hasOnlyOneElementSatisfying(searchMailbox -> MailboxSoftlyAssert.softly(softly)
                        .assertThat(searchMailbox)
                        .isEqualTo(inbox));
            }));
        }

        @Test
        void deleteAfterAFailedDeleteShouldDeleteTheMailbox() throws Exception {
            testee.save(inbox);

            when(mailboxDAO.delete(inboxId))
                .thenReturn(Mono.error(new RuntimeException("mock exception")))
                .thenCallRealMethod();

            doQuietly(() -> testee.delete(inbox));
            doQuietly(() -> testee.delete(inbox));

            SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                softly.assertThatThrownBy(() -> testee.findMailboxById(inboxId))
                    .isInstanceOf(MailboxNotFoundException.class);
                softly.assertThatThrownBy(() -> testee.findMailboxByPath(inboxPath))
                    .isInstanceOf(MailboxNotFoundException.class);
                softly.assertThat(testee.findMailboxWithPathLike(inboxSearchQuery))
                    .isEmpty();
                softly.assertThat(testee.findMailboxWithPathLike(allMailboxesSearchQuery))
                    .isEmpty();
            }));
        }

        @Disabled("JAMES-3056 mailbox name is not updated to INBOX_RENAMED).isEqualTo(" +
            "findMailboxWithPathLike() returns a list with two same mailboxes")
        @Test
        void renameAfterRenameFailOnRetrieveMailboxShouldRenameTheMailbox() throws Exception {
            testee.save(inbox);

            when(mailboxDAO.retrieveMailbox(inboxId))
                .thenReturn(Mono.error(new RuntimeException("mock exception")))
                .thenCallRealMethod();

            doQuietly(() -> testee.save(inboxRenamed));
            doQuietly(() -> testee.save(inboxRenamed));

            SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                MailboxSoftlyAssert.softly(softly)
                    .assertThat(testee.findMailboxById(inboxId))
                    .isEqualTo(inboxRenamed);
                MailboxSoftlyAssert.softly(softly)
                    .assertThat(testee.findMailboxByPath(inboxPathRenamed))
                    .isEqualTo(inboxRenamed);
                softly.assertThat(testee.findMailboxWithPathLike(inboxSearchQuery))
                    .isEmpty();
                softly.assertThat(testee.findMailboxWithPathLike(inboxRenamedSearchQuery))
                    .hasOnlyOneElementSatisfying(searchMailbox -> MailboxSoftlyAssert.softly(softly)
                        .assertThat(searchMailbox)
                        .isEqualTo(inboxRenamed));
                softly.assertThat(testee.findMailboxWithPathLike(allMailboxesSearchQuery))
                    .hasOnlyOneElementSatisfying(searchMailbox -> MailboxSoftlyAssert.softly(softly)
                        .assertThat(searchMailbox)
                        .isEqualTo(inboxRenamed));
            }));
        }

        @Disabled("JAMES-3056 mailbox name is not updated to INBOX_RENAMED")
        @Test
        void renameAfterRenameFailOnGetACLShouldRenameTheMailbox() throws Exception {
            testee.save(inbox);

            when(aclMapper.getACL(inboxId))
                .thenReturn(Mono.error(new RuntimeException("mock exception")))
                .thenCallRealMethod();

            doQuietly(() -> testee.save(inboxRenamed));
            doQuietly(() -> testee.save(inboxRenamed));

            SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                MailboxSoftlyAssert.softly(softly)
                    .assertThat(testee.findMailboxById(inboxId))
                    .isEqualTo(inboxRenamed);
                MailboxSoftlyAssert.softly(softly)
                    .assertThat(testee.findMailboxByPath(inboxPathRenamed))
                    .isEqualTo(inboxRenamed);
                softly.assertThat(testee.findMailboxWithPathLike(inboxSearchQuery))
                    .isEmpty();
                softly.assertThat(testee.findMailboxWithPathLike(inboxRenamedSearchQuery))
                    .hasOnlyOneElementSatisfying(searchMailbox -> MailboxSoftlyAssert.softly(softly)
                        .assertThat(searchMailbox)
                        .isEqualTo(inboxRenamed));
                softly.assertThat(testee.findMailboxWithPathLike(allMailboxesSearchQuery))
                    .hasOnlyOneElementSatisfying(searchMailbox -> MailboxSoftlyAssert.softly(softly)
                        .assertThat(searchMailbox)
                        .isEqualTo(inboxRenamed));
            }));
        }

        @Disabled("JAMES-3056 mailbox name is not updated to INBOX_RENAMED")
        @Test
        void renameAfterRenameFailOnDeletePathShouldRenameTheMailbox() throws Exception {
            testee.save(inbox);

            when(mailboxPathV2DAO.delete(inboxPath))
                .thenReturn(Mono.error(new RuntimeException("mock exception")))
                .thenCallRealMethod();

            doQuietly(() -> testee.save(inboxRenamed));
            doQuietly(() -> testee.save(inboxRenamed));

            SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                MailboxSoftlyAssert.softly(softly)
                    .assertThat(testee.findMailboxById(inboxId))
                    .isEqualTo(inboxRenamed);
                MailboxSoftlyAssert.softly(softly)
                    .assertThat(testee.findMailboxByPath(inboxPathRenamed))
                    .isEqualTo(inboxRenamed);
                softly.assertThat(testee.findMailboxWithPathLike(inboxSearchQuery))
                    .isEmpty();
                softly.assertThat(testee.findMailboxWithPathLike(inboxRenamedSearchQuery))
                    .hasOnlyOneElementSatisfying(searchMailbox ->
                        MailboxSoftlyAssert.softly(softly)
                            .assertThat(searchMailbox)
                            .isEqualTo(inboxRenamed));
                softly.assertThat(testee.findMailboxWithPathLike(allMailboxesSearchQuery))
                    .hasOnlyOneElementSatisfying(searchMailbox -> MailboxSoftlyAssert.softly(softly)
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
    }

    @Disabled("JAMES-2514 Cassandra 3 supports long mailbox names. Hence we can not rely on this for failing")
    @Test
    void saveShouldNotRemoveOldMailboxPathWhenCreatingTheNewMailboxPathFails() throws Exception {
        testee.save(new Mailbox(MAILBOX_PATH, UID_VALIDITY));
        Mailbox mailbox = testee.findMailboxByPath(MAILBOX_PATH);

        Mailbox newMailbox = new Mailbox(tooLongMailboxPath(mailbox.generateAssociatedPath()), UID_VALIDITY, mailbox.getMailboxId());
        assertThatThrownBy(() ->
            testee.save(newMailbox))
            .isInstanceOf(TooLongMailboxNameException.class);

        assertThat(mailboxPathV2DAO.retrieveId(MAILBOX_PATH).blockOptional())
            .isPresent();
    }

    private MailboxPath tooLongMailboxPath(MailboxPath fromMailboxPath) {
        return new MailboxPath(fromMailboxPath, StringUtils.repeat("b", 65537));
    }

    @Test
    void deleteShouldDeleteMailboxAndMailboxPathFromV1Table() {
        mailboxDAO.save(MAILBOX)
            .block();
        mailboxPathDAO.save(MAILBOX_PATH, MAILBOX_ID)
            .block();

        testee.delete(MAILBOX);

        assertThatThrownBy(() -> testee.findMailboxByPath(MAILBOX_PATH))
            .isInstanceOf(MailboxNotFoundException.class);
    }

    @Test
    void deleteShouldDeleteMailboxAndMailboxPathFromV2Table() {
        mailboxDAO.save(MAILBOX)
            .block();
        mailboxPathV2DAO.save(MAILBOX_PATH, MAILBOX_ID)
            .block();

        testee.delete(MAILBOX);

        assertThatThrownBy(() -> testee.findMailboxByPath(MAILBOX_PATH))
            .isInstanceOf(MailboxNotFoundException.class);
    }

    @Test
    void findMailboxByPathShouldReturnMailboxWhenExistsInV1Table() throws Exception {
        mailboxDAO.save(MAILBOX)
            .block();
        mailboxPathDAO.save(MAILBOX_PATH, MAILBOX_ID)
            .block();

        Mailbox mailbox = testee.findMailboxByPath(MAILBOX_PATH);

        assertThat(mailbox.generateAssociatedPath()).isEqualTo(MAILBOX_PATH);
    }

    @Test
    void findMailboxByPathShouldReturnMailboxWhenExistsInV2Table() throws Exception {
        mailboxDAO.save(MAILBOX)
            .block();
        mailboxPathV2DAO.save(MAILBOX_PATH, MAILBOX_ID)
            .block();

        Mailbox mailbox = testee.findMailboxByPath(MAILBOX_PATH);

        assertThat(mailbox.generateAssociatedPath()).isEqualTo(MAILBOX_PATH);
    }

    @Test
    void findMailboxByPathShouldReturnMailboxWhenExistsInBothTables() throws Exception {
        mailboxDAO.save(MAILBOX)
            .block();
        mailboxPathDAO.save(MAILBOX_PATH, MAILBOX_ID)
            .block();
        mailboxPathV2DAO.save(MAILBOX_PATH, MAILBOX_ID)
            .block();

        Mailbox mailbox = testee.findMailboxByPath(MAILBOX_PATH);

        assertThat(mailbox.generateAssociatedPath()).isEqualTo(MAILBOX_PATH);
    }

    @Test
    void deleteShouldRemoveMailboxWhenInBothTables() {
        mailboxDAO.save(MAILBOX)
            .block();
        mailboxPathDAO.save(MAILBOX_PATH, MAILBOX_ID)
            .block();
        mailboxPathV2DAO.save(MAILBOX_PATH, MAILBOX_ID)
            .block();

        testee.delete(MAILBOX);

        assertThatThrownBy(() -> testee.findMailboxByPath(MAILBOX_PATH))
            .isInstanceOf(MailboxNotFoundException.class);
    }

    @Test
    void deleteShouldRemoveMailboxWhenInV1Tables() {
        mailboxDAO.save(MAILBOX)
            .block();
        mailboxPathDAO.save(MAILBOX_PATH, MAILBOX_ID)
            .block();

        testee.delete(MAILBOX);

        assertThatThrownBy(() -> testee.findMailboxByPath(MAILBOX_PATH))
            .isInstanceOf(MailboxNotFoundException.class);
    }

    @Test
    void deleteShouldRemoveMailboxWhenInV2Table() {
        mailboxDAO.save(MAILBOX)
            .block();
        mailboxPathV2DAO.save(MAILBOX_PATH, MAILBOX_ID)
            .block();

        testee.delete(MAILBOX);

        assertThatThrownBy(() -> testee.findMailboxByPath(MAILBOX_PATH))
            .isInstanceOf(MailboxNotFoundException.class);
    }

    @Test
    void findMailboxByPathShouldThrowWhenDoesntExistInBothTables() {
        mailboxDAO.save(MAILBOX)
            .block();

        assertThatThrownBy(() -> testee.findMailboxByPath(MAILBOX_PATH))
            .isInstanceOf(MailboxNotFoundException.class);
    }

    @Test
    void findMailboxWithPathLikeShouldReturnMailboxesWhenExistsInV1Table() {
        mailboxDAO.save(MAILBOX)
            .block();
        mailboxPathDAO.save(MAILBOX_PATH, MAILBOX_ID)
            .block();
    
        List<Mailbox> mailboxes = testee.findMailboxWithPathLike(MailboxQuery.builder()
            .privateNamespace()
            .username(USER)
            .expression(Wildcard.INSTANCE)
            .build()
            .asUserBound());

        assertThat(mailboxes).containsOnly(MAILBOX);
    }

    @Test
    void findMailboxWithPathLikeShouldReturnMailboxesWhenExistsInBothTables() {
        mailboxDAO.save(MAILBOX)
            .block();
        mailboxPathDAO.save(MAILBOX_PATH, MAILBOX_ID)
            .block();
        mailboxPathV2DAO.save(MAILBOX_PATH, MAILBOX_ID)
            .block();

        List<Mailbox> mailboxes = testee.findMailboxWithPathLike(MailboxQuery.builder()
            .privateNamespace()
            .username(USER)
            .expression(Wildcard.INSTANCE)
            .build()
            .asUserBound());

        assertThat(mailboxes).containsOnly(MAILBOX);
    }

    @Test
    void findMailboxWithPathLikeShouldReturnMailboxesWhenExistsInV2Table() {
        mailboxDAO.save(MAILBOX)
            .block();
        mailboxPathV2DAO.save(MAILBOX_PATH, MAILBOX_ID)
            .block();
    
        List<Mailbox> mailboxes = testee.findMailboxWithPathLike(MailboxQuery.builder()
            .privateNamespace()
            .username(USER)
            .expression(Wildcard.INSTANCE)
            .build()
            .asUserBound());

        assertThat(mailboxes).containsOnly(MAILBOX);
    }

    @Test
    void hasChildrenShouldReturnChildWhenExistsInV1Table() {
        mailboxDAO.save(MAILBOX)
            .block();
        mailboxPathDAO.save(MAILBOX_PATH, MAILBOX_ID)
            .block();
        CassandraId childMailboxId = CassandraId.timeBased();
        MailboxPath childMailboxPath = MailboxPath.forUser(USER, "name.child");
        Mailbox childMailbox = new Mailbox(childMailboxPath, UID_VALIDITY, childMailboxId);
        mailboxDAO.save(childMailbox)
            .block();
        mailboxPathDAO.save(childMailboxPath, childMailboxId)
            .block();
    
        boolean hasChildren = testee.hasChildren(MAILBOX, '.');

        assertThat(hasChildren).isTrue();
    }

    @Test
    void hasChildrenShouldReturnChildWhenExistsInBothTables() {
        mailboxDAO.save(MAILBOX)
            .block();
        mailboxPathDAO.save(MAILBOX_PATH, MAILBOX_ID)
            .block();
        mailboxPathV2DAO.save(MAILBOX_PATH, MAILBOX_ID)
            .block();
        CassandraId childMailboxId = CassandraId.timeBased();
        MailboxPath childMailboxPath = MailboxPath.forUser(USER, "name.child");
        Mailbox childMailbox = new Mailbox(childMailboxPath, UID_VALIDITY, childMailboxId);
        mailboxDAO.save(childMailbox)
            .block();
        mailboxPathDAO.save(childMailboxPath, childMailboxId)
            .block();

        boolean hasChildren = testee.hasChildren(MAILBOX, '.');

        assertThat(hasChildren).isTrue();
    }

    @Test
    void hasChildrenShouldReturnChildWhenExistsInV2Table() {
        mailboxDAO.save(MAILBOX)
            .block();
        mailboxPathV2DAO.save(MAILBOX_PATH, MAILBOX_ID)
            .block();
        CassandraId childMailboxId = CassandraId.timeBased();
        MailboxPath childMailboxPath = MailboxPath.forUser(USER, "name.child");
        Mailbox childMailbox = new Mailbox(childMailboxPath, UID_VALIDITY, childMailboxId);
        mailboxDAO.save(childMailbox)
            .block();
        mailboxPathV2DAO.save(childMailboxPath, childMailboxId)
            .block();
    
        boolean hasChildren = testee.hasChildren(MAILBOX, '.');
    
        assertThat(hasChildren).isTrue();
    }

    @Test
    void findMailboxWithPathLikeShouldRemoveDuplicatesAndKeepV2() {
        mailboxDAO.save(MAILBOX).block();
        mailboxPathV2DAO.save(MAILBOX_PATH, MAILBOX_ID).block();

        mailboxDAO.save(MAILBOX_BIS).block();
        mailboxPathDAO.save(MAILBOX_PATH, MAILBOX_ID_2).block();

        assertThat(testee.findMailboxWithPathLike(
            MailboxQuery.builder()
                .privateNamespace()
                .username(USER)
                .expression(Wildcard.INSTANCE)
                .build()
                .asUserBound()))
            .containsOnly(MAILBOX);
    }
}
