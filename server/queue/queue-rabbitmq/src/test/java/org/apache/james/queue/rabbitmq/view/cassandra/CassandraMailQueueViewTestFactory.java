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

package org.apache.james.queue.rabbitmq.view.cassandra;

import java.time.Clock;
import java.util.Optional;

import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.mail.MimeMessageStore;
import org.apache.james.eventsourcing.eventstore.cassandra.CassandraEventStore;
import org.apache.james.eventsourcing.eventstore.cassandra.EventStoreDao;
import org.apache.james.eventsourcing.eventstore.cassandra.JsonEventSerializer;
import org.apache.james.queue.rabbitmq.MailQueueName;
import org.apache.james.queue.rabbitmq.view.cassandra.configuration.CassandraMailQueueViewConfiguration;
import org.apache.james.queue.rabbitmq.view.cassandra.configuration.CassandraMailQueueViewConfigurationModule;
import org.apache.james.queue.rabbitmq.view.cassandra.configuration.EventsourcingConfigurationManagement;

import com.datastax.driver.core.Session;

public class CassandraMailQueueViewTestFactory {

    public static CassandraMailQueueView.Factory factory(Clock clock, Session session,
                                                         CassandraMailQueueViewConfiguration configuration,
                                                         MimeMessageStore.Factory mimeMessageStoreFactory) {
        HashBlobId.Factory blobIdFactory = new HashBlobId.Factory();

        EnqueuedMailsDAO enqueuedMailsDao = new EnqueuedMailsDAO(session, blobIdFactory);
        BrowseStartDAO browseStartDao = new BrowseStartDAO(session);
        DeletedMailsDAO deletedMailsDao = new DeletedMailsDAO(session);

        CassandraMailQueueBrowser cassandraMailQueueBrowser = new CassandraMailQueueBrowser(browseStartDao, deletedMailsDao, enqueuedMailsDao, mimeMessageStoreFactory, configuration, clock);
        CassandraMailQueueMailStore cassandraMailQueueMailStore = new CassandraMailQueueMailStore(enqueuedMailsDao, browseStartDao, configuration, clock);
        CassandraMailQueueMailDelete cassandraMailQueueMailDelete = new CassandraMailQueueMailDelete(deletedMailsDao, browseStartDao, cassandraMailQueueBrowser, configuration);


        EventsourcingConfigurationManagement eventsourcingConfigurationManagement = new EventsourcingConfigurationManagement(new CassandraEventStore(new EventStoreDao(session,
            JsonEventSerializer.forModules(CassandraMailQueueViewConfigurationModule.MAIL_QUEUE_VIEW_CONFIGURATION).withoutNestedType())));

        return new CassandraMailQueueView.Factory(
            cassandraMailQueueMailStore,
            cassandraMailQueueBrowser,
            cassandraMailQueueMailDelete,
            eventsourcingConfigurationManagement,
            configuration);
    }

    public static boolean isInitialized(Session session, MailQueueName mailQueueName) {
        BrowseStartDAO browseStartDao = new BrowseStartDAO(session);
        return browseStartDao.findBrowseStart(mailQueueName)
            .map(Optional::ofNullable)
            .defaultIfEmpty(Optional.empty())
            .block()
            .isPresent();
    }
}
