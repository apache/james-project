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

package org.apache.james.mailbox.jpa;

import java.time.Instant;

import javax.persistence.EntityManagerFactory;

import org.apache.james.backends.jpa.JpaTestCluster;
import org.apache.james.events.EventBusTestFixture;
import org.apache.james.events.InVMEventBus;
import org.apache.james.events.MemoryEventDeadLetters;
import org.apache.james.events.delivery.InVmEventDelivery;
import org.apache.james.mailbox.Authenticator;
import org.apache.james.mailbox.Authorizator;
import org.apache.james.mailbox.acl.MailboxACLResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.jpa.mail.JPAModSeqProvider;
import org.apache.james.mailbox.jpa.mail.JPAUidProvider;
import org.apache.james.mailbox.jpa.openjpa.OpenJPAMailboxManager;
import org.apache.james.mailbox.store.SessionProviderImpl;
import org.apache.james.mailbox.store.StoreMailboxAnnotationManager;
import org.apache.james.mailbox.store.StoreRightManager;
import org.apache.james.mailbox.store.extractor.DefaultTextExtractor;
import org.apache.james.mailbox.store.mail.NaiveThreadIdGuessingAlgorithm;
import org.apache.james.mailbox.store.mail.model.DefaultMessageId;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.mailbox.store.quota.QuotaComponents;
import org.apache.james.mailbox.store.search.MessageSearchIndex;
import org.apache.james.mailbox.store.search.SimpleMessageSearchIndex;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.modules.data.JPAConfiguration;
import org.apache.james.utils.UpdatableTickingClock;

public class JpaMailboxManagerProvider {

    private static final int LIMIT_ANNOTATIONS = 3;
    private static final int LIMIT_ANNOTATION_SIZE = 30;

    public static OpenJPAMailboxManager provideMailboxManager(JpaTestCluster jpaTestCluster) {
        EntityManagerFactory entityManagerFactory = jpaTestCluster.getEntityManagerFactory();

        JPAConfiguration jpaConfiguration = JPAConfiguration.builder()
            .driverName("driverName")
            .driverURL("driverUrl")
            .attachmentStorage(true)
            .build();

        JPAMailboxSessionMapperFactory mf = new JPAMailboxSessionMapperFactory(entityManagerFactory, new JPAUidProvider(entityManagerFactory), new JPAModSeqProvider(entityManagerFactory), jpaConfiguration);

        MailboxACLResolver aclResolver = new UnionMailboxACLResolver();
        MessageParser messageParser = new MessageParser();

        Authenticator noAuthenticator = null;
        Authorizator noAuthorizator = null;

        InVMEventBus eventBus = new InVMEventBus(new InVmEventDelivery(new RecordingMetricFactory()), EventBusTestFixture.RETRY_BACKOFF_CONFIGURATION, new MemoryEventDeadLetters());
        StoreRightManager storeRightManager = new StoreRightManager(mf, aclResolver, eventBus);
        StoreMailboxAnnotationManager annotationManager = new StoreMailboxAnnotationManager(mf, storeRightManager,
            LIMIT_ANNOTATIONS, LIMIT_ANNOTATION_SIZE);
        SessionProviderImpl sessionProvider = new SessionProviderImpl(noAuthenticator, noAuthorizator);
        QuotaComponents quotaComponents = QuotaComponents.disabled(sessionProvider, mf);
        MessageSearchIndex index = new SimpleMessageSearchIndex(mf, mf, new DefaultTextExtractor(), new JPAAttachmentContentLoader());

        return new OpenJPAMailboxManager(mf, sessionProvider,
            messageParser, new DefaultMessageId.Factory(),
            eventBus, annotationManager,
            storeRightManager, quotaComponents, index, new NaiveThreadIdGuessingAlgorithm(), new UpdatableTickingClock(Instant.now()));
    }
}
