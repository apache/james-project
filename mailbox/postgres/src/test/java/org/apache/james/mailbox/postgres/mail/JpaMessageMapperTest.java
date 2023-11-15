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

package org.apache.james.mailbox.postgres.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import javax.mail.Flags;
import javax.persistence.EntityManagerFactory;

import org.apache.james.backends.jpa.JPAConfiguration;
import org.apache.james.backends.jpa.JpaTestCluster;
import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.backends.postgres.PostgresModule;
import org.apache.james.backends.postgres.utils.SimpleJamesPostgresConnectionFactory;
import org.apache.james.mailbox.FlagsBuilder;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.postgres.JPAMailboxFixture;
import org.apache.james.mailbox.postgres.PostgresMailboxSessionMapperFactory;
import org.apache.james.mailbox.postgres.user.PostgresSubscriptionModule;
import org.apache.james.mailbox.store.FlagsUpdateCalculator;
import org.apache.james.mailbox.store.mail.model.MapperProvider;
import org.apache.james.mailbox.store.mail.model.MessageMapperTest;
import org.apache.james.utils.UpdatableTickingClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class JpaMessageMapperTest extends MessageMapperTest {
    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(PostgresModule.aggregateModules(
        PostgresMailboxModule.MODULE,
        PostgresSubscriptionModule.MODULE));

    static final JpaTestCluster JPA_TEST_CLUSTER = JpaTestCluster.create(JPAMailboxFixture.MAILBOX_PERSISTANCE_CLASSES);
    
    @Override
    protected MapperProvider createMapperProvider() {
        EntityManagerFactory entityManagerFactory = JPA_TEST_CLUSTER.getEntityManagerFactory();

        JPAConfiguration jpaConfiguration = JPAConfiguration.builder()
            .driverName("driverName")
            .driverURL("driverUrl")
            .build();

        PostgresMailboxSessionMapperFactory mapperFactory = new PostgresMailboxSessionMapperFactory(entityManagerFactory,
            new JPAUidProvider(entityManagerFactory),
            new JPAModSeqProvider(entityManagerFactory),
            jpaConfiguration,
            new SimpleJamesPostgresConnectionFactory(postgresExtension.getConnectionFactory()));

        return new JPAMapperProvider(JPA_TEST_CLUSTER, mapperFactory);
    }

    @Override
    protected UpdatableTickingClock updatableTickingClock() {
        return null;
    }

    @AfterEach
    void cleanUp() {
        JPA_TEST_CLUSTER.clear(JPAMailboxFixture.MAILBOX_TABLE_NAMES);
    }

    @Test
    @Override
    public void flagsAdditionShouldReturnAnUpdatedFlagHighlightingTheAddition() throws MailboxException {
        saveMessages();
        messageMapper.updateFlags(benwaInboxMailbox, message1.getUid(), new FlagsUpdateCalculator(new Flags(Flags.Flag.FLAGGED), MessageManager.FlagsUpdateMode.REPLACE));
        ModSeq modSeq = messageMapper.getHighestModSeq(benwaInboxMailbox);

        // JPA does not support MessageId
        assertThat(messageMapper.updateFlags(benwaInboxMailbox, message1.getUid(), new FlagsUpdateCalculator(new Flags(Flags.Flag.SEEN), MessageManager.FlagsUpdateMode.ADD)))
            .contains(UpdatedFlags.builder()
                .uid(message1.getUid())
                .modSeq(modSeq.next())
                .oldFlags(new Flags(Flags.Flag.FLAGGED))
                .newFlags(new FlagsBuilder().add(Flags.Flag.SEEN, Flags.Flag.FLAGGED).build())
                .build());
    }

    @Test
    @Override
    public void flagsReplacementShouldReturnAnUpdatedFlagHighlightingTheReplacement() throws MailboxException {
        saveMessages();
        ModSeq modSeq = messageMapper.getHighestModSeq(benwaInboxMailbox);
        Optional<UpdatedFlags> updatedFlags = messageMapper.updateFlags(benwaInboxMailbox, message1.getUid(),
            new FlagsUpdateCalculator(new Flags(Flags.Flag.FLAGGED), MessageManager.FlagsUpdateMode.REPLACE));

        // JPA does not support MessageId
        assertThat(updatedFlags)
            .contains(UpdatedFlags.builder()
                .uid(message1.getUid())
                .modSeq(modSeq.next())
                .oldFlags(new Flags())
                .newFlags(new Flags(Flags.Flag.FLAGGED))
                .build());
    }

    @Test
    @Override
    public void flagsRemovalShouldReturnAnUpdatedFlagHighlightingTheRemoval() throws MailboxException {
        saveMessages();
        messageMapper.updateFlags(benwaInboxMailbox, message1.getUid(), new FlagsUpdateCalculator(new FlagsBuilder().add(Flags.Flag.FLAGGED, Flags.Flag.SEEN).build(), MessageManager.FlagsUpdateMode.REPLACE));
        ModSeq modSeq = messageMapper.getHighestModSeq(benwaInboxMailbox);

        // JPA does not support MessageId
        assertThat(messageMapper.updateFlags(benwaInboxMailbox, message1.getUid(), new FlagsUpdateCalculator(new Flags(Flags.Flag.SEEN), MessageManager.FlagsUpdateMode.REMOVE)))
            .contains(
                UpdatedFlags.builder()
                    .uid(message1.getUid())
                    .modSeq(modSeq.next())
                    .oldFlags(new FlagsBuilder().add(Flags.Flag.SEEN, Flags.Flag.FLAGGED).build())
                    .newFlags(new Flags(Flags.Flag.FLAGGED))
                    .build());
    }

    @Test
    @Override
    public void userFlagsUpdateShouldReturnCorrectUpdatedFlags() throws MailboxException {
        saveMessages();
        ModSeq modSeq = messageMapper.getHighestModSeq(benwaInboxMailbox);

        // JPA does not support MessageId
        assertThat(messageMapper.updateFlags(benwaInboxMailbox, message1.getUid(), new FlagsUpdateCalculator(new Flags(USER_FLAG), MessageManager.FlagsUpdateMode.ADD)))
            .contains(
                UpdatedFlags.builder()
                    .uid(message1.getUid())
                    .modSeq(modSeq.next())
                    .oldFlags(new Flags())
                    .newFlags(new Flags(USER_FLAG))
                    .build());
    }

    @Test
    @Override
    public void userFlagsUpdateShouldReturnCorrectUpdatedFlagsWhenNoop() throws MailboxException {
        saveMessages();

        // JPA does not support MessageId
        assertThat(
            messageMapper.updateFlags(benwaInboxMailbox,message1.getUid(),
                new FlagsUpdateCalculator(new Flags(USER_FLAG), MessageManager.FlagsUpdateMode.REMOVE)))
            .contains(
                UpdatedFlags.builder()
                    .uid(message1.getUid())
                    .modSeq(message1.getModSeq())
                    .oldFlags(new Flags())
                    .newFlags(new Flags())
                    .build());
    }

    @Nested
    @Disabled("JPA does not support saveDate.")
    class SaveDateTests {

    }
}
