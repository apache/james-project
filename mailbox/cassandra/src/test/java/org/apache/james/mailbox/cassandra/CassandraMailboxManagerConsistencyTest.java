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
package org.apache.james.mailbox.cassandra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.init.CassandraTypesProvider;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxPathDAOImpl;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxPathV2DAO;
import org.apache.james.mailbox.cassandra.mail.MailboxAggregateModule;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.mailbox.model.search.Wildcard;
import org.apache.james.mailbox.store.PreDeletionHooks;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.datastax.driver.core.Session;
import com.github.fge.lambdas.Throwing;
import com.github.fge.lambdas.runnable.ThrowingRunnable;

import reactor.core.publisher.Mono;

class CassandraMailboxManagerConsistencyTest {

    private static final Username USER = Username.of("user");
    private static final String INBOX = "INBOX";

    @RegisterExtension
    static CassandraClusterExtension cassandra = new CassandraClusterExtension(MailboxAggregateModule.MODULE_WITH_QUOTA);

    private CassandraMailboxManager testee;
    private MailboxSession mailboxSession;

    private MailboxPath inboxPath;
    private MailboxQuery.UserBound allMailboxesSearchQuery;

    private CassandraMailboxDAO mailboxDAO;
    private CassandraMailboxPathDAOImpl mailboxPathDAO;
    private CassandraMailboxPathV2DAO mailboxPathV2DAO;

    @BeforeEach
    void setUp() {
        Session session = cassandra.getCassandraCluster().getConf();
        CassandraTypesProvider typesProvider = cassandra.getCassandraCluster().getTypesProvider();

        mailboxDAO = spy(new CassandraMailboxDAO(session, typesProvider));
        mailboxPathDAO = spy(new CassandraMailboxPathDAOImpl(session, typesProvider));
        mailboxPathV2DAO = spy(new CassandraMailboxPathV2DAO(session, CassandraUtils.WITH_DEFAULT_CONFIGURATION));

        testee = CassandraMailboxManagerProvider.provideMailboxManager(
            session,
            typesProvider,
            PreDeletionHooks.NO_PRE_DELETION_HOOK,
            binder -> binder.bind(CassandraMailboxDAO.class).toInstance(mailboxDAO),
            binder -> binder.bind(CassandraMailboxPathDAOImpl.class).toInstance(mailboxPathDAO),
            binder -> binder.bind(CassandraMailboxPathV2DAO.class).toInstance(mailboxPathV2DAO));

        mailboxSession = testee.createSystemSession(USER);

        inboxPath = MailboxPath.forUser(USER, INBOX);
        allMailboxesSearchQuery = MailboxQuery.builder()
            .userAndNamespaceFrom(inboxPath)
            .expression(Wildcard.INSTANCE)
            .build()
            .asUserBound();
    }

    @Nested
    class FailsOnCreate {

        @Test
        void createMailboxShouldBeConsistentWhenMailboxDaoFails() {
            doReturn(Mono.error(new RuntimeException("mock exception")))
                .when(mailboxDAO)
                .save(any(Mailbox.class));

            doQuietly(() -> testee.createMailbox(inboxPath, mailboxSession));

            SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                softly.assertThat(testee.search(allMailboxesSearchQuery, mailboxSession))
                    .isEmpty();
                softly.assertThat(testee.list(mailboxSession))
                    .isEmpty();
            }));
        }

        @Test
        void createMailboxShouldBeConsistentWhenMailboxPathDaoFails() {
            doReturn(Mono.error(new RuntimeException("mock exception")))
                .when(mailboxPathV2DAO)
                .save(eq(inboxPath), isA(CassandraId.class));

            doQuietly(() -> testee.createMailbox(inboxPath, mailboxSession));

            SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                softly.assertThat(testee.search(allMailboxesSearchQuery, mailboxSession))
                    .isEmpty();
                softly.assertThat(testee.list(mailboxSession))
                    .isEmpty();
            }));
        }

        @Disabled("JAMES-3056 createMailbox() return an empty Optional")
        @Test
        void createMailboxAfterAFailedCreationShouldCreateTheMailboxWhenMailboxDaoFails() throws Exception {
            doReturn(Mono.error(new RuntimeException("mock exception")))
                .doCallRealMethod()
                .when(mailboxDAO)
                .save(any(Mailbox.class));

            doQuietly(() -> testee.createMailbox(inboxPath, mailboxSession));

            assertThat(testee.createMailbox(inboxPath, mailboxSession))
                .isNotEmpty();
        }

        @Test
        void createMailboxAfterAFailedCreationShouldCreateTheMailboxWhenMailboxPathDaoFails() throws Exception {
            doReturn(Mono.error(new RuntimeException("mock exception")))
                .doCallRealMethod()
                .when(mailboxPathV2DAO)
                .save(eq(inboxPath), isA(CassandraId.class));

            doQuietly(() -> testee.createMailbox(inboxPath, mailboxSession));

            MailboxId inboxId = testee.createMailbox(inboxPath, mailboxSession)
                .get();

            SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                softly.assertThat(testee.search(allMailboxesSearchQuery, mailboxSession))
                    .hasOnlyOneElementSatisfying(mailboxMetaData -> {
                        softly.assertThat(mailboxMetaData.getId()).isEqualTo(inboxId);
                        softly.assertThat(mailboxMetaData.getPath()).isEqualTo(inboxPath);
                    });
                softly.assertThat(testee.list(mailboxSession))
                    .containsExactly(inboxPath);
            }));
        }

        @Disabled("JAMES-3056 createMailbox() return an empty Optional")
        @Test
        void createMailboxAfterDeletingShouldCreateTheMailboxWhenMailboxDaoFails() throws Exception {
            doReturn(Mono.error(new RuntimeException("mock exception")))
                .doCallRealMethod()
                .when(mailboxDAO)
                .save(any(Mailbox.class));

            doQuietly(() -> testee.createMailbox(inboxPath, mailboxSession));
            doQuietly(() -> testee.deleteMailbox(inboxPath, mailboxSession));

            assertThat(testee.createMailbox(inboxPath, mailboxSession))
                .isNotEmpty();
        }

        @Test
        void createMailboxAfterDeletingShouldCreateTheMailboxWhenMailboxPathDaoFails() throws Exception {
            doReturn(Mono.error(new RuntimeException("mock exception")))
                .doCallRealMethod()
                .when(mailboxPathV2DAO)
                .save(eq(inboxPath), isA(CassandraId.class));

            doQuietly(() -> testee.createMailbox(inboxPath, mailboxSession));
            doQuietly(() -> testee.deleteMailbox(inboxPath, mailboxSession));

            MailboxId inboxId = testee.createMailbox(inboxPath, mailboxSession)
                .get();

            SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                softly.assertThat(testee.search(allMailboxesSearchQuery, mailboxSession))
                    .hasOnlyOneElementSatisfying(mailboxMetaData -> {
                        softly.assertThat(mailboxMetaData.getId()).isEqualTo(inboxId);
                        softly.assertThat(mailboxMetaData.getPath()).isEqualTo(inboxPath);
                    });
                softly.assertThat(testee.list(mailboxSession))
                    .containsExactly(inboxPath);
            }));
        }
    }

    private void doQuietly(ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (Throwable th) {
            // ignore
        }
    }
}
