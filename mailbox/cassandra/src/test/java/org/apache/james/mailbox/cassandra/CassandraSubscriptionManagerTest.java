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

import java.time.Clock;

import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.core.Username;
import org.apache.james.events.EventBusTestFixture;
import org.apache.james.events.InVMEventBus;
import org.apache.james.events.MemoryEventDeadLetters;
import org.apache.james.events.delivery.InVmEventDelivery;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.SubscriptionManagerContract;
import org.apache.james.mailbox.cassandra.mail.CassandraACLMapper;
import org.apache.james.mailbox.cassandra.mail.CassandraApplicableFlagDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraAttachmentDAOV2;
import org.apache.james.mailbox.cassandra.mail.CassandraDeletedMessageDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraFirstUnseenDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxCounterDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxPathV3DAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxRecentsDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageDAOV3;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdToImapUidDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraModSeqProvider;
import org.apache.james.mailbox.cassandra.mail.CassandraThreadDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraThreadLookupDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraUidProvider;
import org.apache.james.mailbox.cassandra.mail.CassandraUserMailboxRightsDAO;
import org.apache.james.mailbox.cassandra.mail.task.RecomputeMailboxCountersService;
import org.apache.james.mailbox.cassandra.modules.CassandraAnnotationModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxModule;
import org.apache.james.mailbox.cassandra.modules.CassandraSubscriptionModule;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.BatchSizes;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.apache.james.mailbox.store.user.model.Subscription;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Test Cassandra subscription against some general purpose written code.
 */
class CassandraSubscriptionManagerTest implements SubscriptionManagerContract {

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraModule.aggregateModules(
        CassandraSubscriptionModule.MODULE,
        CassandraAnnotationModule.MODULE,
        CassandraSchemaVersionModule.MODULE,
        CassandraMailboxModule.MODULE));

    private SubscriptionManager subscriptionManager;
    private CassandraMailboxSessionMapperFactory mailboxSessionMapperFactory;

    @Override
    public SubscriptionManager getSubscriptionManager() {
        return subscriptionManager;
    }

    @BeforeEach
    void setUp() {
        CassandraThreadDAO threadDAO = null;
        CassandraThreadLookupDAO threadLookupDAO = null;
        CassandraMessageIdToImapUidDAO imapUidDAO = null;
        CassandraMessageDAOV3 messageDAOV3 = null;
        CassandraMessageIdDAO messageIdDAO = null;
        CassandraMailboxCounterDAO mailboxCounterDAO = null;
        CassandraMailboxRecentsDAO mailboxRecentsDAO = null;
        CassandraMailboxDAO mailboxDAO = null;
        CassandraMailboxPathV3DAO mailboxPathV3DAO = new CassandraMailboxPathV3DAO(cassandraCluster.getCassandraCluster().getConf());
        CassandraFirstUnseenDAO firstUnseenDAO = null;
        CassandraApplicableFlagDAO applicableFlagDAO = null;
        CassandraDeletedMessageDAO deletedMessageDAO = null;
        CassandraAttachmentDAOV2 attachmentDAOV2 = null;
        CassandraACLMapper aclMapper = null;
        CassandraUserMailboxRightsDAO userMailboxRightsDAO = null;
        BlobStore blobStore = null;
        CassandraUidProvider uidProvider = null;
        CassandraModSeqProvider modSeqProvider = null;
        RecomputeMailboxCountersService recomputeMailboxCountersService = null;

        mailboxSessionMapperFactory =  new CassandraMailboxSessionMapperFactory(
            uidProvider,
            modSeqProvider,
            cassandraCluster.getCassandraCluster().getConf(),
            threadDAO,
            threadLookupDAO,
            messageDAOV3,
            messageIdDAO,
            imapUidDAO,
            mailboxCounterDAO,
            mailboxRecentsDAO,
            mailboxDAO,
            mailboxPathV3DAO,
            firstUnseenDAO,
            applicableFlagDAO,
            attachmentDAOV2,
            deletedMessageDAO,
            blobStore,
            aclMapper,
            userMailboxRightsDAO,
            recomputeMailboxCountersService,
            CassandraConfiguration.DEFAULT_CONFIGURATION,
            BatchSizes.defaultValues(),
            Clock.systemUTC());

        InVMEventBus eventBus = new InVMEventBus(new InVmEventDelivery(new RecordingMetricFactory()), EventBusTestFixture.RETRY_BACKOFF_CONFIGURATION, new MemoryEventDeadLetters());

        subscriptionManager = new StoreSubscriptionManager(
            mailboxSessionMapperFactory,
            mailboxSessionMapperFactory,
            eventBus);
    }

    @Test
    void legacySubscriptionsCanBeListed() throws Exception {
        mailboxSessionMapperFactory.createSubscriptionMapper(SESSION)
            .save(new Subscription(SESSION.getUser(), "whatever"));

        assertThat(Flux.from(subscriptionManager.subscriptionsReactive(SESSION)).collectList().block())
            .containsOnly(MailboxPath.forUser(SESSION.getUser(), "whatever"));
    }

    @Test
    void legacySubscriptionsCanBeRemovedReactive() throws Exception {
        mailboxSessionMapperFactory.createSubscriptionMapper(SESSION)
            .save(new Subscription(SESSION.getUser(), "whatever"));

        Mono.from(subscriptionManager.unsubscribeReactive(MailboxPath.forUser(SESSION.getUser(), "whatever"), SESSION))
            .block();

        assertThat(Flux.from(subscriptionManager.subscriptionsReactive(SESSION)).collectList().block())
            .isEmpty();
    }

    @Test
    void removingADelegatedSubscriptionShouldNotUnsubscribeLegacySubscriptionReactive() throws Exception {
        mailboxSessionMapperFactory.createSubscriptionMapper(SESSION)
            .save(new Subscription(SESSION.getUser(), "whatever"));

        Mono.from(subscriptionManager.unsubscribeReactive(MailboxPath.forUser(Username.of("alice"), "whatever"), SESSION))
            .block();

        assertThat(Flux.from(subscriptionManager.subscriptionsReactive(SESSION)).collectList().block())
            .containsOnly(MailboxPath.forUser(SESSION.getUser(), "whatever"));
    }

    @Test
    void legacySubscriptionsCanBeRemoved() throws Exception {
        mailboxSessionMapperFactory.createSubscriptionMapper(SESSION)
            .save(new Subscription(SESSION.getUser(), "whatever"));

        subscriptionManager.unsubscribe(SESSION, MailboxPath.forUser(SESSION.getUser(), "whatever"));

        assertThat(Flux.from(subscriptionManager.subscriptionsReactive(SESSION)).collectList().block())
            .isEmpty();
    }

    @Test
    void removingADelegatedSubscriptionShouldNotUnsubscribeLegacySubscription() throws Exception {
        mailboxSessionMapperFactory.createSubscriptionMapper(SESSION)
            .save(new Subscription(SESSION.getUser(), "whatever"));

        subscriptionManager.unsubscribe(SESSION, MailboxPath.forUser(Username.of("alice"), "whatever"));

        assertThat(Flux.from(subscriptionManager.subscriptionsReactive(SESSION)).collectList().block())
            .containsOnly(MailboxPath.forUser(SESSION.getUser(), "whatever"));
    }
}
